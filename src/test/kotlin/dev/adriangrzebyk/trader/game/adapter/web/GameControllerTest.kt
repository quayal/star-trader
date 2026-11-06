package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.adapter.dynamodb.DynamoDbLocal
import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import dev.adriangrzebyk.trader.login.adapter.dynamodb.sessionItemId
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistrar
import org.springframework.test.web.servlet.client.RestTestClient

// Blank the Resend settings so a developer's RESEND_* variables don't send real email to example.com.
// Games go to DynamoDB Local, the same store bootRun uses, so the wiring and the mapper are exercised over HTTP.
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["RESEND_API_KEY=", "RESEND_FROM_ADDRESS="]
)
class GameControllerTest {

	@LocalServerPort
	private var port: Int = 0

	@Autowired
	private lateinit var sessionStore: SessionStore

	@Autowired
	private lateinit var gameStore: GameStore

	private lateinit var restTestClient: RestTestClient

	@BeforeEach
	fun setUp() {
		restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
	}

	companion object {

		@JvmStatic
		@BeforeAll
		fun requireDynamoDbLocal() = DynamoDbLocal.assumeAvailable()
	}

	// Found by @SpringBootTest as a nested configuration; points the game store at the DynamoDB Local container.
	@TestConfiguration
	class DynamoDbLocalProperties {

		@Bean
		fun useDynamoDbLocal() = DynamicPropertyRegistrar { registry ->
			registry.add("star-trader.dynamodb.endpoint-override") { DynamoDbLocal.endpoint }
			registry.add("star-trader.dynamodb.table-name") { "games-controller-test" }
		}
	}

	@Test
	fun shouldCreateAGameWithTheSignedInPlayerAsItsFirstPlayer() {
		val token = signIn("creator@example.com")

		val game = createGame(token, """{"turns": 20}""")
			.expectStatus().isCreated
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(game.id).isNotBlank()
		assertThat(game.turns).isEqualTo(20)
		assertThat(game.status).isEqualTo("LOBBY")
		assertThat(game.creatorEmail).isEqualTo("creator@example.com")
		assertThat(
			game.players
		).containsExactly(PlayerResponseDto("creator@example.com", "Adrian", "Adrian Star Traders", emptyList(), emptyList()))
		assertThat(GameResponseDto(gameStore.find(game.id)!!, "creator@example.com")).isEqualTo(game)
		assertThat(game.turn).isNull()
		assertThat(game.you).isEqualTo(YouResponseDto(0, emptyList(), emptyList(), null))
	}

	@Test
	fun shouldGiveEachGameItsOwnId() {
		val token = signIn("two-games@example.com")

		val first = createGame(token, """{"turns": 5}""").expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val second = createGame(token, """{"turns": 10}""").expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(first.id).isNotEqualTo(second.id)
		assertThat(gameStore.find(first.id)!!.turns).isEqualTo(5)
		assertThat(gameStore.find(second.id)!!.turns).isEqualTo(10)
	}

	@Test
	fun shouldRejectALengthOutsideOneToFifty() {
		val token = signIn("bad-length@example.com")

		createGame(token, """{"turns": 51}""").expectStatus().isBadRequest
		createGame(token, """{"turns": 0}""").expectStatus().isBadRequest
		createGame(token, """{}""").expectStatus().isBadRequest
	}

	@Test
	fun shouldRejectAFractionalOrNonNumericLength() {
		val token = signIn("not-whole@example.com")

		createGame(token, """{"turns": 2.5}""").expectStatus().isBadRequest
		createGame(token, """{"turns": "many"}""").expectStatus().isBadRequest
	}

	@Test
	fun shouldRejectACallerWithoutASession() {
		createGame(null, """{"turns": 20}""").expectStatus().isUnauthorized
		createGame("not-a-real-token", """{"turns": 20}""").expectStatus().isUnauthorized
	}

	@Test
	fun shouldAddTheSignedInPlayerToTheGame() {
		val game = createdGame(signIn("host@example.com"))
		val joinerToken = signIn("guest@example.com", "Grace", "Grace Freight")

		val joined = joinGame(joinerToken, game.id)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(joined.players).containsExactly(
			PlayerResponseDto("host@example.com", "Adrian", "Adrian Star Traders", emptyList(), emptyList()),
			PlayerResponseDto("guest@example.com", "Grace", "Grace Freight", emptyList(), emptyList())
		)
		assertThat(joined.status).isEqualTo("LOBBY")
		assertThat(GameResponseDto(gameStore.find(game.id)!!, "guest@example.com")).isEqualTo(joined)
	}

	@Test
	fun shouldRejectJoiningAnUnknownGame() {
		joinGame(signIn("lost@example.com"), "no-such-game")
			.expectStatus().isNotFound
			.expectBody(String::class.java).isEqualTo("This game doesn't exist")
	}

	@Test
	fun shouldRejectAPlayerWhoIsAlreadyInTheGame() {
		val token = signIn("twice@example.com")
		val game = createdGame(token)

		joinGame(token, game.id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This player is already in the game")
		assertThat(gameStore.find(game.id)!!.players).hasSize(1)
	}

	@Test
	fun shouldRejectASeventhPlayer() {
		val game = createdGame(signIn("full-1@example.com"))
		(2..6).forEach { joinGame(signIn("full-$it@example.com"), game.id).expectStatus().isOk }

		joinGame(signIn("full-7@example.com"), game.id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game is full")
		assertThat(gameStore.find(game.id)!!.players).hasSize(6)
	}

	@Test
	fun shouldRejectJoiningAGameThatHasStarted() {
		// Nothing can start a game through the API until E02-S05, so this one is stored already running.
		val host = Player.newcomer("started-host@example.com", "Host", "Host Corp")
		gameStore.create(Game("started-game", 20, GameStatus.RUNNING, host.email, listOf(host), null, emptyList()))

		joinGame(signIn("late@example.com"), "started-game")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game has already started")
	}

	@Test
	fun shouldRejectJoiningWithoutASession() {
		val game = createdGame(signIn("open-door@example.com"))

		joinGame(null, game.id).expectStatus().isUnauthorized
		joinGame("not-a-real-token", game.id).expectStatus().isUnauthorized
		assertThat(gameStore.find(game.id)!!.players).hasSize(1)
	}

	@Test
	fun shouldShowTheGameToEachOfItsPlayers() {
		val creatorToken = signIn("poll-host@example.com")
		val joinerToken = signIn("poll-guest@example.com", "Grace", "Grace Freight")
		val game = createdGame(creatorToken)
		joinGame(joinerToken, game.id).expectStatus().isOk

		val seenByCreator = pollGame(creatorToken, game.id)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val seenByJoiner = pollGame(joinerToken, game.id)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(seenByCreator).isEqualTo(GameResponseDto(gameStore.find(game.id)!!, "poll-host@example.com"))
		assertThat(seenByCreator.players.map { it.email }).containsExactly("poll-host@example.com", "poll-guest@example.com")
		// Everything but `you` is shared; `you` is each viewer's own.
		assertThat(seenByJoiner.copy(you = null)).isEqualTo(seenByCreator.copy(you = null))
	}

	@Test
	fun shouldRefuseTheGameToASignedInPlayerWhoIsNotInIt() {
		val game = createdGame(signIn("private-host@example.com"))

		val body = pollGame(signIn("outsider@example.com"), game.id)
			.expectStatus().isForbidden
			.expectBody(String::class.java).returnResult().responseBody

		assertThat(body).isEqualTo("You are not a player in this game")
	}

	@Test
	fun shouldRejectPollingAnUnknownGame() {
		pollGame(signIn("poll-lost@example.com"), "no-such-game")
			.expectStatus().isNotFound
			.expectBody(String::class.java).isEqualTo("This game doesn't exist")
	}

	@Test
	fun shouldNotServeASessionItemAsAGame() {
		val token = signIn("session-probe@example.com")

		pollGame(token, sessionItemId(token)).expectStatus().isNotFound
	}

	@Test
	fun shouldRejectPollingWithoutASession() {
		val game = createdGame(signIn("poll-open-door@example.com"))

		pollGame(null, game.id).expectStatus().isUnauthorized
		pollGame("not-a-real-token", game.id).expectStatus().isUnauthorized
	}

	@Test
	fun shouldStartTheGameForEveryPlayer() {
		val creatorToken = signIn("start-host@example.com")
		val joinerToken = signIn("start-guest@example.com", "Grace", "Grace Freight")
		val game = createdGame(creatorToken)
		joinGame(joinerToken, game.id).expectStatus().isOk

		val started = startGame(creatorToken, game.id)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(started.status).isEqualTo("RUNNING")
		assertThat(started.turn).isEqualTo(TurnResponseDto(1, "BIDDING", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null))
		assertThat(started.you!!.money).isEqualTo(300)
		assertThat(started.you!!.ships).hasSize(2).allMatch { it.cargo.isEmpty() && it.cargoUsed == 0 && it.cargoSpaces == 10 }
		assertThat(started.board.map { it.id }).containsExactlyInAnyOrder(
			"GAMMA_LEPORIS",
			"TAU_CETI",
			"EPSILON_ERIDANI",
			"BETA_HYDRI",
			"MU_HERCULIS",
			"SIGMA_DRACONIS"
		)
		assertThat(started.players).allSatisfy { player ->
			assertThat(player.ships).hasSize(2).allMatch { it.location == "PORT" }
			assertThat(player.ships.map { it.system }).doesNotHaveDuplicates()
		}
		assertThat(started.log.first()).isEqualTo(LogEntryResponseDto(1, "Game started: 2 players, 20 turns"))
		joinGame(signIn("start-late@example.com"), game.id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game has already started")
	}

	@Test
	fun shouldNeverSendAnotherPlayersMoney() {
		val creatorToken = signIn("money-host@example.com")
		val joinerToken = signIn("money-guest@example.com", "Grace", "Grace Freight")
		val game = createdGame(creatorToken)
		joinGame(joinerToken, game.id).expectStatus().isOk
		startGame(creatorToken, game.id).expectStatus().isOk

		val raw = pollGame(joinerToken, game.id).expectStatus().isOk
			.expectBody(String::class.java).returnResult().responseBody!!

		// Exactly one money field on the wire: the caller's own, under "you" (R29).
		assertThat(Regex("\"money\"").findAll(raw).count()).isEqualTo(1)
		assertThat(raw).contains("\"you\":{\"money\":300,")
	}

	@Test
	fun shouldNeverSendAnotherPlayersCargo() {
		val (creatorToken, joinerToken, gameId) = startedGame("cargo")
		// The joiner's first ship carries goods; only the joiner may see them (S02-7, A05).
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != "cargo-guest@example.com") {
						player
					} else {
						player.copy(ships = listOf(player.ships[0].copy(cargo = mapOf(Goods.POLY to 4, Goods.CHIPS to 12))) + player.ships.drop(1))
					}
				}
			)
		}

		val seenByCreator = pollGame(creatorToken, gameId).expectStatus().isOk
			.expectBody(String::class.java).returnResult().responseBody!!
		val seenByJoiner = pollGame(joinerToken, gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(seenByCreator).doesNotContain("\"POLY\":4").doesNotContain("\"CHIPS\":12")
		assertThat(Regex("\"cargo\"").findAll(seenByCreator).count()).isEqualTo(2) // the creator's own two ships
		assertThat(seenByJoiner.you!!.ships.first().cargo).isEqualTo(mapOf("CHIPS" to 12, "POLY" to 4))
		assertThat(seenByJoiner.you!!.ships.first().cargoUsed).isEqualTo(4)
	}

	@Test
	fun shouldLetOnlyTheCreatorStartTheGame() {
		val creatorToken = signIn("only-host@example.com")
		val joinerToken = signIn("only-guest@example.com", "Grace", "Grace Freight")
		val game = createdGame(creatorToken)
		joinGame(joinerToken, game.id).expectStatus().isOk

		startGame(joinerToken, game.id)
			.expectStatus().isForbidden
			.expectBody(String::class.java).isEqualTo("Only the creator can start the game")
		startGame(signIn("only-outsider@example.com"), game.id).expectStatus().isForbidden
		assertThat(gameStore.find(game.id)!!.status).isEqualTo(GameStatus.LOBBY)
	}

	@Test
	fun shouldRefuseToStartWithFewerThanTwoPlayersOrTwice() {
		val creatorToken = signIn("twice-host@example.com")
		val game = createdGame(creatorToken)

		startGame(creatorToken, game.id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("At least 2 players are needed to start")

		joinGame(signIn("twice-guest@example.com"), game.id).expectStatus().isOk
		startGame(creatorToken, game.id).expectStatus().isOk
		startGame(creatorToken, game.id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game has already started")
	}

	@Test
	fun shouldRejectStartingAnUnknownGameOrWithoutASession() {
		startGame(signIn("start-lost@example.com"), "no-such-game")
			.expectStatus().isNotFound
			.expectBody(String::class.java).isEqualTo("This game doesn't exist")
		val game = createdGame(signIn("start-open-door@example.com"))
		startGame(null, game.id).expectStatus().isUnauthorized
		startGame("not-a-real-token", game.id).expectStatus().isUnauthorized
	}

	@Test
	fun shouldCloseBiddingForEveryoneWhenTheLastPlayerSignalsDone() {
		val (creatorToken, joinerToken, gameId) = startedGame("done")

		val afterFirst = signalDone(joinerToken, gameId)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(
			afterFirst.turn
		).isEqualTo(TurnResponseDto(1, "BIDDING", listOf("done-guest@example.com"), emptyList(), emptyList(), emptyList(), emptyList(), null))

		signalDone(creatorToken, gameId).expectStatus().isOk

		val polled = pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(polled.turn!!.phase).isEqualTo("SHIP_JUMPS")
		assertThat(polled.turn!!.doneBidding).isEmpty()
		assertThat(polled.turn!!.initiative).containsExactlyInAnyOrder("done-host@example.com", "done-guest@example.com")
	}

	@Test
	fun shouldTellAPlayerWhoseDoneArrivesAfterBiddingClosed() {
		val (creatorToken, joinerToken, gameId) = startedGame("late")
		signalDone(joinerToken, gameId).expectStatus().isOk
		signalDone(creatorToken, gameId).expectStatus().isOk

		signalDone(joinerToken, gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Bidding has closed")
	}

	@Test
	fun shouldRefuseDoneFromOutsidersBeforeTheStartAndWithoutASession() {
		val (_, _, gameId) = startedGame("outsider")
		signalDone(signIn("outsider-stranger@example.com"), gameId)
			.expectStatus().isForbidden
			.expectBody(String::class.java).isEqualTo("You are not a player in this game")
		signalDone(signIn("outsider-lost@example.com"), "no-such-game").expectStatus().isNotFound
		signalDone(null, gameId).expectStatus().isUnauthorized

		val lobbyToken = signIn("outsider-lobby@example.com")
		signalDone(lobbyToken, createdGame(lobbyToken).id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game hasn't started")
	}

	@Test
	fun shouldPlaceAnOfferAndShowItOnlyToItsPlayer() {
		val (creatorToken, joinerToken, gameId) = startedGame("offer")

		val placed = placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}""")
			.expectStatus().isCreated
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		val mine = YourOfferResponseDto("TAU_CETI", "Tau Ceti", "POLY", "BUY", 8)
		assertThat(placed.you!!.offers).containsExactly(mine)
		assertThat(pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.offers)
			.containsExactly(mine)
		assertThat(pollGame(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.offers)
			.isEmpty()
	}

	@Test
	fun shouldNeverSendAnotherPlayersOffers() {
		val (creatorToken, joinerToken, gameId) = startedGame("secret")
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "SELL", "price": 19}""").expectStatus().isCreated

		// Every response the creator can get during Bidding: the poll, their own offer, their own done (R17, A05).
		val seenByCreator = listOf(
			pollGame(creatorToken, gameId),
			placeOffer(creatorToken, gameId, """{"system": "TAU_CETI", "goods": "ISO", "type": "BUY", "price": 3}"""),
			signalDone(creatorToken, gameId)
		).map { it.expectBody(String::class.java).returnResult().responseBody!! }

		seenByCreator.forEach { raw ->
			assertThat(raw).doesNotContain("\"price\":19").doesNotContain("SELL")
			assertThat(Regex("\"offers\"").findAll(raw).count()).isEqualTo(1) // only "you.offers"
		}
	}

	@Test
	fun shouldRefuseAnOfferTheRulesDoNotAllow() {
		val (_, joinerToken, gameId) = startedGame("bad-offer")
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}""").expectStatus().isCreated

		listOf(
			"""{"system": "TAU_CETI", "goods": "ISO", "type": "BUY", "price": 21}""" to "Price must be a whole number from 1 to 20.",
			"""{"system": "TAU_CETI", "goods": "ISO", "type": "BUY", "price": 2.5}""" to "Price must be a whole number from 1 to 20.",
			"""{"system": "TAU_CETI", "goods": "ISO", "type": "BUY"}""" to "Price must be a whole number from 1 to 20.",
			"""{"system": "TAU_CETI", "goods": "POLY", "type": "SELL", "price": 9}""" to "You already have an offer on POLY at Tau Ceti this turn.",
			"""{"system": "GAMMA_LEPORIS", "goods": "FOOD", "type": "BUY", "price": 9}""" to "FOOD isn't traded at Gamma Leporis.",
			"""{"system": "NOWHERE", "goods": "ISO", "type": "BUY", "price": 9}""" to "Choose a star system.",
			"""{"system": "TAU_CETI", "goods": "GOLD", "type": "BUY", "price": 9}""" to "Choose a good.",
			"""{"system": "TAU_CETI", "goods": "ISO", "type": "SWAP", "price": 9}""" to "Choose Buy or Sell."
		).forEach { (body, message) ->
			placeOffer(joinerToken, gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo(message)
		}
		assertThat(gameStore.find(gameId)!!.turn!!.offers).hasSize(1)
	}

	@Test
	fun shouldRefuseTheDictatorsOfferForTheirGood() {
		val (_, joinerToken, gameId) = startedGame("dictator")
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != "dictator-guest@example.com") {
						player
					} else {
						player.copy(marketPositions = listOf(MarketPosition(StarSystem.TAU_CETI, Goods.POLY, MarketTier.DICTATOR)))
					}
				}
			)
		}

		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("You're Dictator of POLY here — you place that offer when it resolves in Trading.")
	}

	@Test
	fun shouldTellAPlayerWhoseOfferArrivesAfterBiddingClosed() {
		val (creatorToken, joinerToken, gameId) = startedGame("offer-late")
		signalDone(joinerToken, gameId).expectStatus().isOk
		signalDone(creatorToken, gameId).expectStatus().isOk

		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Bidding has closed")
		assertThat(gameStore.find(gameId)!!.turn!!.offers).isEmpty()
	}

	@Test
	fun shouldRefuseOffersFromOutsidersForUnknownGamesAndWithoutASession() {
		val (_, _, gameId) = startedGame("offer-outsider")
		val body = """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}"""

		placeOffer(signIn("offer-stranger@example.com"), gameId, body)
			.expectStatus().isForbidden
			.expectBody(String::class.java).isEqualTo("You are not a player in this game")
		placeOffer(signIn("offer-lost@example.com"), "no-such-game", body).expectStatus().isNotFound
		placeOffer(null, gameId, body).expectStatus().isUnauthorized
		val lobbyToken = signIn("offer-lobby@example.com")
		placeOffer(lobbyToken, createdGame(lobbyToken).id, body)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game hasn't started")
	}

	@Test
	fun shouldPlaceAndChangeABidAndShowItOnlyToItsPlayer() {
		val (creatorToken, joinerToken, gameId) = startedGame("bid")

		val placed = placeBid(joinerToken, gameId, """{"amount": 40}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(placed.you!!.bid).isEqualTo(40)

		placeBid(joinerToken, gameId, """{"amount": 25}""").expectStatus().isOk
		assertThat(pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.bid)
			.isEqualTo(25)
		assertThat(pollGame(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.bid)
			.isNull()
	}

	@Test
	fun shouldNeverSendAnotherPlayersBidOrMoney() {
		val (creatorToken, joinerToken, gameId) = startedGame("bid-secret")
		placeBid(joinerToken, gameId, """{"amount": 137}""").expectStatus().isOk
		signalDone(joinerToken, gameId).expectStatus().isOk

		// Every response the creator can get: the poll, their own bid, the done that closes Bidding, the poll after (R29, S05-6).
		val seenByCreator = listOf(
			pollGame(creatorToken, gameId),
			placeBid(creatorToken, gameId, """{"amount": 5}"""),
			signalDone(creatorToken, gameId),
			pollGame(creatorToken, gameId)
		).map { it.expectBody(String::class.java).returnResult().responseBody!! }

		seenByCreator.forEach { raw ->
			assertThat(raw).doesNotContain("\"bid\":137").doesNotContain("\"money\":163").doesNotContain("\"amount\"")
			assertThat(Regex("\"bid\"").findAll(raw).count()).isEqualTo(1) // only "you.bid"
			assertThat(Regex("\"money\"").findAll(raw).count()).isEqualTo(1) // only "you.money"
		}
		assertThat(seenByCreator.last()).contains("\"money\":295")
	}

	@Test
	fun shouldChargeEveryBidAndPublishTheOrderWhenBiddingCloses() {
		val (creatorToken, joinerToken, gameId) = startedGame("all-pay")
		placeBid(joinerToken, gameId, """{"amount": 40}""").expectStatus().isOk
		placeBid(creatorToken, gameId, """{"amount": 10}""").expectStatus().isOk
		signalDone(joinerToken, gameId).expectStatus().isOk

		val closed = signalDone(creatorToken, gameId)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(closed.you!!.money).isEqualTo(290)
		assertThat(pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.money)
			.isEqualTo(260)
		assertThat(closed.turn!!.initiative).containsExactlyInAnyOrder("all-pay-host@example.com", "all-pay-guest@example.com")
		val logged = closed.log.last()
		assertThat(logged.turn).isEqualTo(1)
		assertThat(logged.text).startsWith("Initiative order (bid + d2): ")
		assertThat(logged.text.substringAfter(": ")).doesNotContainPattern("[0-9]") // R29: the order, never an amount
	}

	@Test
	fun shouldSendTheLogAsPublicEventsByTurnWithoutOffersBidsOrMoney() {
		val (creatorToken, joinerToken, gameId) = startedGame("log")
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "SELL", "price": 17}""").expectStatus().isCreated
		placeBid(joinerToken, gameId, """{"amount": 23}""").expectStatus().isOk
		signalDone(joinerToken, gameId).expectStatus().isOk
		signalDone(creatorToken, gameId).expectStatus().isOk

		val raw = pollGame(creatorToken, gameId).expectBody(String::class.java).returnResult().responseBody!!
		val log = pollGame(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.log

		// S06-1: every entry carries its turn; S06-3: none names an offer, a bid or money (R17, R29).
		assertThat(raw).contains("\"log\":[{\"turn\":1,\"text\":\"Game started: 2 players, 20 turns\"}")
		assertThat(log.map { it.turn }).containsOnly(1)
		assertThat(log.map { it.text }).hasSize(3).noneMatch { text ->
			listOf("17", "23", "277", "HT", "POLY", "SELL").any { it in text }
		}
	}

	@Test
	fun shouldRefuseABidTheRulesDoNotAllow() {
		val (_, joinerToken, gameId) = startedGame("bad-bid")

		listOf(
			"""{"amount": 301}""" to "You have 300 HT. Enter 300 or less.",
			"""{"amount": -1}""" to "A bid can't be negative. Enter 0 to bid nothing.",
			"""{"amount": 2.5}""" to "Enter a whole number of HT, 0 or more.",
			"""{}""" to "Enter a whole number of HT, 0 or more."
		).forEach { (body, message) ->
			placeBid(joinerToken, gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo(message)
		}
		assertThat(gameStore.find(gameId)!!.turn!!.bids).isEmpty()
	}

	@Test
	fun shouldTellAPlayerWhoseBidArrivesAfterBiddingClosed() {
		val (creatorToken, joinerToken, gameId) = startedGame("bid-late")
		signalDone(joinerToken, gameId).expectStatus().isOk
		signalDone(creatorToken, gameId).expectStatus().isOk

		placeBid(joinerToken, gameId, """{"amount": 40}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Bidding has closed")
		assertThat(gameStore.find(gameId)!!.turn!!.bids).isEmpty()
	}

	@Test
	fun shouldRefuseBidsFromOutsidersForUnknownGamesAndWithoutASession() {
		val (_, _, gameId) = startedGame("bid-outsider")
		val body = """{"amount": 40}"""

		placeBid(signIn("bid-stranger@example.com"), gameId, body)
			.expectStatus().isForbidden
			.expectBody(String::class.java).isEqualTo("You are not a player in this game")
		placeBid(signIn("bid-lost@example.com"), "no-such-game", body).expectStatus().isNotFound
		placeBid(null, gameId, body).expectStatus().isUnauthorized
		val lobbyToken = signIn("bid-lobby@example.com")
		placeBid(lobbyToken, createdGame(lobbyToken).id, body)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game hasn't started")
	}

	@Test
	fun shouldLetPlayersPickTheirPlaceInInitiativeOrderOnceBiddingCloses() {
		val (creatorToken, joinerToken, gameId) = startedGame("pick")
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(closed.turn!!.turnOrder).containsExactly(null, null)
		val (first, second) = closed.turn!!.initiative

		// E04-S01: the first in initiative order may take last place; the other player gets the place left (S01-6).
		val picked = pickPlace("token-$first", gameId, """{"place": 2}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(picked.turn!!.turnOrder).containsExactly(second, first)
		assertThat(picked.turn!!.phase).isEqualTo("SHIP_JUMPS")
		assertThat(picked.log.map { it.text }.last()).startsWith("Turn order (for this turn): ")
		assertThat(pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.turn!!.turnOrder)
			.containsExactly(second, first)
	}

	@Test
	fun shouldRefuseAPickOutOfTurnOrForAPlaceThatIsNotFree() {
		val (creatorToken, joinerToken, gameId) = startedGame("bad-pick")
		pickPlace(joinerToken, gameId, """{"place": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Ship-jumps hasn't started.")
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = closed.turn!!.initiative
		val firstName = if (first == "bad-pick-guest@example.com") "Grace Freight" else "Adrian Star Traders"

		pickPlace("token-$second", gameId, """{"place": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("It's $firstName's turn to choose a place.")
		listOf("""{"place": 3}""", """{"place": 0}""", """{"place": 1.5}""", """{}""").forEach { body ->
			pickPlace("token-$first", gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo("Choose a free place in the turn order.")
		}
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		pickPlace("token-$second", gameId, """{"place": 2}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("The turn order is already set.")
	}

	@Test
	fun shouldRefuseAPickFromSomeoneWhoIsNotAPlayerOrForAGameThatDoesNotExist() {
		val (_, _, gameId) = startedGame("pick-outsider")

		pickPlace(signIn("pick-outsider-stranger@example.com"), gameId, """{"place": 1}""").expectStatus().isForbidden
		pickPlace(signIn("pick-outsider-host@example.com"), "missing-game", """{"place": 1}""").expectStatus().isNotFound
		pickPlace(null, gameId, """{"place": 1}""").expectStatus().isUnauthorized
	}

	@Test
	fun shouldLetPlayersJumpTheirShipsInTurnOrderThenMoveToTrading() {
		val (first, second, gameId) = orderedGame("jump")
		val ship = pollGame("token-$first", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
			.players.first { it.email == first }.ships.first()
		val destination = StarSystem.entries.first { it.name != ship.system }

		// E04-S02: the first place jumps one ship; it arrives at once, in space, and the jump is public.
		val jumped = jumpShip("token-$first", gameId, """{"ship": "${ship.name}", "destination": "${destination.name}"}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val seenByOther = pollGame("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(seenByOther.players.first { it.email == first }.ships.first())
			.isEqualTo(ShipResponseDto(ship.name, destination.name, destination.displayName, "SPACE"))
		assertThat(seenByOther.turn!!.jumpedShips).containsExactly(ship.name)
		assertThat(jumped.log.last().text).endsWith("— ${ship.name} → ${destination.displayName}")

		// E04-S03: the jump finishes once the player chooses where the ship ends up.
		assertThat(seenByOther.turn!!.landingShip).isEqualTo(ship.name)
		finishJumping("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Choose where ${ship.name} ends up first.")
		val landed = landShip("token-$first", gameId, """{"location": "PLANET"}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(landed.players.first { it.email == first }.ships.first().location).isEqualTo("PLANET")
		assertThat(landed.turn!!.landingShip).isNull()
		assertThat(landed.log.last().text).endsWith("— ${ship.name} lands on the planet (${destination.displayName})")

		finishJumping("token-$first", gameId).expectStatus().isOk
		val trading = finishJumping("token-$second", gameId)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(trading.turn!!.phase).isEqualTo("TRADING")
		assertThat(trading.turn!!.doneJumping).containsExactly(first, second)
	}

	@Test
	fun shouldNeverShowAnotherPlayersCargoAfterAJump() {
		val (first, second, gameId) = orderedGame("jump-cargo")
		val ship = pollGame("token-$first", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
			.players.first { it.email == first }.ships.first()
		val destination = StarSystem.entries.first { it.name != ship.system }
		jumpShip("token-$first", gameId, """{"ship": "${ship.name}", "destination": "${destination.name}"}""").expectStatus().isOk

		// S02-10, R29: where the ship is, yes; what it carries, no.
		pollGame("token-$second", gameId).expectBody()
			.jsonPath("$.players[*].ships[*].cargo").doesNotExist()
			.jsonPath("$.players[*].money").doesNotExist()
	}

	@Test
	fun shouldRefuseAJumpOutOfTurnOrThatBreaksAJumpRule() {
		val (creatorToken, joinerToken, gameId) = startedGame("bad-jump")
		jumpShip(joinerToken, gameId, """{"ship": "Lantern", "destination": "TAU_CETI"}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Ship-jumps hasn't started.")
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = closed.turn!!.initiative
		finishJumping("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("The turn order isn't set yet.")
		val ordered = pickPlace("token-$first", gameId, """{"place": 1}""").expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val firstName = if (first == "bad-jump-guest@example.com") "Grace Freight" else "Adrian Star Traders"
		val ship = ordered.players.first { it.email == first }.ships.first()
		val otherShip = ordered.players.first { it.email == second }.ships.first()

		finishJumping("token-$second", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("It's $firstName's turn to jump.")
		jumpShip("token-$first", gameId, """{"ship": "${ship.name}", "destination": "${ship.system}"}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("${ship.name} is already at ${ship.systemName}. Pick another destination.")
		listOf("""{"ship": "${otherShip.name}", "destination": "TAU_CETI"}""", """{"destination": "TAU_CETI"}""").forEach { body ->
			jumpShip("token-$first", gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo("Choose one of your ships that hasn't jumped this turn.")
		}
		listOf("""{"ship": "${ship.name}", "destination": "ALPHA_CENTAURI"}""", """{"ship": "${ship.name}"}""").forEach { body ->
			jumpShip("token-$first", gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo("Choose a destination system.")
		}
	}

	@Test
	fun shouldRefuseAJumpFromSomeoneWhoIsNotAPlayerOrForAGameThatDoesNotExist() {
		val (_, _, gameId) = orderedGame("jump-outsider")
		val body = """{"ship": "Wayfarer", "destination": "TAU_CETI"}"""

		jumpShip(signIn("jump-outsider-stranger@example.com"), gameId, body).expectStatus().isForbidden
		jumpShip(signIn("jump-outsider-lost@example.com"), "missing-game", body).expectStatus().isNotFound
		jumpShip(null, gameId, body).expectStatus().isUnauthorized
		finishJumping(signIn("jump-outsider-stranger2@example.com"), gameId).expectStatus().isForbidden
		finishJumping(signIn("jump-outsider-lost2@example.com"), "missing-game").expectStatus().isNotFound
		finishJumping(null, gameId).expectStatus().isUnauthorized
	}

	@Test
	fun shouldRefuseALandingWithNoShipWaitingOrWithoutALocation() {
		val (first, second, gameId) = orderedGame("bad-landing")
		landShip("token-$first", gameId, """{"location": "PORT"}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("No ship is waiting to land.")
		val ship = pollGame("token-$first", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
			.players.first { it.email == first }.ships.first()
		val destination = StarSystem.entries.first { it.name != ship.system }
		jumpShip("token-$first", gameId, """{"ship": "${ship.name}", "destination": "${destination.name}"}""").expectStatus().isOk

		listOf("""{"location": "ORBIT"}""", """{}""").forEach { body ->
			landShip("token-$first", gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo("Choose where the ship ends up.")
		}
		landShip("token-$second", gameId, """{"location": "PORT"}""").expectStatus().isEqualTo(HttpStatus.CONFLICT)
		landShip(signIn("bad-landing-stranger@example.com"), gameId, """{"location": "PORT"}""").expectStatus().isForbidden
		landShip(signIn("bad-landing-lost@example.com"), "missing-game", """{"location": "PORT"}""").expectStatus().isNotFound
		landShip(null, gameId, """{"location": "PORT"}""").expectStatus().isUnauthorized
	}

	/**
	 * A two-player game for this test's own emails with Bidding closed and the turn order set, the first in
	 * initiative order taking 1st: the email in 1st place, the one in 2nd, the game id. Tokens are "token-{email}".
	 */
	private fun orderedGame(prefix: String): Triple<String, String, String> {
		val (creatorToken, joinerToken, gameId) = startedGame(prefix)
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = closed.turn!!.initiative
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		return Triple(first, second, gameId)
	}

	private fun jumpShip(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/jumps", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun landShip(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/landing", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun finishJumping(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/finish", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	/** A started two-player game for this test's own emails: the creator's token, the joiner's token, the game id. */
	private fun startedGame(prefix: String): Triple<String, String, String> {
		val creatorToken = signIn("$prefix-host@example.com")
		val joinerToken = signIn("$prefix-guest@example.com", "Grace", "Grace Freight")
		val game = createdGame(creatorToken)
		joinGame(joinerToken, game.id).expectStatus().isOk
		startGame(creatorToken, game.id).expectStatus().isOk
		return Triple(creatorToken, joinerToken, game.id)
	}

	private fun signalDone(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/bidding/done", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun placeOffer(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/offers", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun pickPlace(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/turn-order", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun placeBid(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.put().uri("/api/games/{id}/bidding/bid", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun startGame(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/start", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun signIn(email: String, name: String = "Adrian", corporationName: String = "Adrian Star Traders"): String {
		val token = "token-$email"
		sessionStore.store(Session(token, name, corporationName, email))
		return token
	}

	private fun createdGame(token: String): GameResponseDto =
		createGame(token, """{"turns": 20}""").expectBody(GameResponseDto::class.java).returnResult().responseBody!!

	private fun joinGame(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/join", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun pollGame(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.get().uri("/api/games/{id}", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun createGame(token: String?, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games").contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}
}
