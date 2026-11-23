package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.Connections
import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.OpportunityPassed
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.game.Warehouse
import dev.adriangrzebyk.trader.game.adapter.dynamodb.DynamoDbLocal
import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import dev.adriangrzebyk.trader.game.battle.BattleShip
import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import dev.adriangrzebyk.trader.login.adapter.dynamodb.sessionItemId
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
// Compression is off: the test client leaks a connection per unread gzipped body and hangs (lessons, 2026-11-18).
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["RESEND_API_KEY=", "RESEND_FROM_ADDRESS=", "server.compression.enabled=false"]
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
			registry.add("star-trader.dynamodb.region") { DynamoDbLocal.REGION }
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
		assertThat(game.creatorId).isEqualTo("p1")
		assertThat(
			game.players
		).containsExactly(
			PlayerResponseDto(
				"p1", "Adrian", "Adrian Star Traders", emptyList(), emptyList(), emptyList(), emptyList(),
				ConnectionsResponseDto(
					0,
					0,
					0
				),
				0,
				20,
				0,
				0,
				emptyList()
			)
		)
		assertThat(GameResponseDto(gameStore.find(game.id)!!, "creator@example.com")).isEqualTo(game)
		assertThat(game.turn).isNull()
		assertThat(
			game.you
		).isEqualTo(
			YouResponseDto(
				"p1", 0, emptyList(), emptyList(), emptyList(), null, null, null, emptyList(), null, null, null, null, null,
				AssetsResponseDto(0, 0, 0, emptyList(), emptyList(), emptyList()),
				null, emptyList(), 10, emptyList(), emptyList(), null, emptyList(), null, null, emptyList(), emptyList(), emptyList(), emptyList()
			)
		)
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
	fun shouldAddTheSignedInPlayerToTheGameOnceTheCreatorAcceptsTheirRequest() {
		val hostToken = signIn("host@example.com")
		val game = createdGame(hostToken)
		val joinerToken = signIn("guest@example.com", "Grace", "Grace Freight")

		val asked = requestToJoin(joinerToken, game.id)
			.expectStatus().isOk
			.expectBody(OpenGameResponseDto::class.java).returnResult().responseBody!!
		assertThat(asked).isEqualTo(
			OpenGameResponseDto(game.id, 20, CreatorResponseDto("Adrian", "Adrian Star Traders"), listOf("Adrian Star Traders"), 1, 6, "ASKED")
		)
		pollGame(joinerToken, game.id).expectStatus().isForbidden
		val requestId = JoinRequestIds(game.id).of("guest@example.com")
		val seenByHost = pollGame(hostToken, game.id).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(seenByHost.joinRequests).containsExactly(JoinRequestResponseDto(requestId, "Grace", "Grace Freight"))

		val joined = acceptJoinRequest(hostToken, game.id, requestId)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(joined.players).containsExactly(
			PlayerResponseDto(
				"p1", "Adrian", "Adrian Star Traders", emptyList(), emptyList(), emptyList(), emptyList(),
				ConnectionsResponseDto(
					0,
					0,
					0
				),
				0,
				20,
				0,
				0,
				emptyList()
			),
			PlayerResponseDto(
				"p2", "Grace", "Grace Freight", emptyList(), emptyList(), emptyList(), emptyList(),
				ConnectionsResponseDto(
					0,
					0,
					0
				),
				0, 20, 0, 0, emptyList()
			)
		)
		assertThat(joined.joinRequests).isEmpty()
		assertThat(joined.status).isEqualTo("LOBBY")
		pollGame(joinerToken, game.id).expectStatus().isOk
	}

	@Test
	fun shouldListOpenGamesWithWhereTheCallerStandsAndNoEmails() {
		val hostToken = signIn("list-host@example.com", "Ada", "Orion Freight")
		val viewerToken = signIn("list-viewer@example.com", "Grace", "Vega Lines")
		val hosted = createdGame(hostToken)
		val asked = createdGame(signIn("list-other@example.com", "Bo", "Bo Haulage"))
		requestToJoin(viewerToken, asked.id).expectStatus().isOk
		val own = createdGame(viewerToken)

		val answer = restTestClient.get().uri("/api/open-games").header("Authorization", "Bearer $viewerToken").exchange()
			.expectStatus().isOk.expectBody(String::class.java).returnResult().responseBody!!

		assertThat(answer).doesNotContain("@example.com")
		val rows = restTestClient.get().uri("/api/open-games").header("Authorization", "Bearer $viewerToken").exchange()
			.expectBody(Array<OpenGameResponseDto>::class.java).returnResult().responseBody!!.toList()
		assertThat(rows.filter { it.id in listOf(hosted.id, asked.id, own.id) }.map { it.id to it.standing })
			.containsExactly(own.id to "IN", asked.id to "ASKED", hosted.id to "NONE")
		assertThat(rows.single { it.id == hosted.id }.creator).isEqualTo(CreatorResponseDto("Ada", "Orion Freight"))
	}

	@Test
	fun shouldLetTheCreatorDeclineAndTheRequesterWithdraw() {
		val hostToken = signIn("decline-host@example.com")
		val game = createdGame(hostToken)
		val joinerToken = signIn("decline-guest@example.com", "Grace", "Grace Freight")
		val requestId = JoinRequestIds(game.id).of("decline-guest@example.com")

		requestToJoin(joinerToken, game.id).expectStatus().isOk
		restTestClient.post().uri("/api/games/{id}/join-requests/{r}/decline", game.id, requestId)
			.header("Authorization", "Bearer $hostToken").exchange().expectStatus().isOk
		assertThat(gameStore.find(game.id)!!.joinRequests).isEmpty()

		requestToJoin(joinerToken, game.id).expectStatus().isOk
		val withdrawn = restTestClient.delete().uri("/api/games/{id}/join-requests/mine", game.id)
			.header("Authorization", "Bearer $joinerToken").exchange().expectStatus().isOk
			.expectBody(OpenGameResponseDto::class.java).returnResult().responseBody!!
		assertThat(withdrawn.standing).isEqualTo("NONE")
		assertThat(gameStore.find(game.id)!!.players).hasSize(1)
	}

	@Test
	fun shouldLetOnlyTheCreatorAcceptAndRefuseARequestThatIsNoLongerWaiting() {
		val hostToken = signIn("only-host@example.com")
		val game = createdGame(hostToken)
		val guestToken = signIn("only-guest@example.com")
		joinGame(guestToken, game.id).expectStatus().isOk
		requestToJoin(signIn("only-asker@example.com"), game.id).expectStatus().isOk
		val requestId = JoinRequestIds(game.id).of("only-asker@example.com")

		acceptJoinRequest(guestToken, game.id, requestId)
			.expectStatus().isForbidden
			.expectBody(String::class.java).isEqualTo("Only the creator can accept or decline a request to join")
		acceptJoinRequest(hostToken, game.id, "r-unknown")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("That request to join is no longer waiting")
		assertThat(gameStore.find(game.id)!!.players).hasSize(2)
	}

	@Test
	fun shouldAnswerAnUnchangedPollWithAnEmpty304() {
		// A poll that sends back the last ETag gets 304 and no body while the caller's view is unchanged, and the whole
		// game again once something changed (creator, 2026-11-18: polling over a metered tunnel).
		val token = signIn("etag@example.com")
		val game = createdGame(token)
		val etag = pollGame(token, game.id).expectStatus().isOk.returnResult(String::class.java).responseHeaders.eTag!!

		restTestClient.get().uri("/api/games/{id}", game.id).header("Authorization", "Bearer $token").header("If-None-Match", etag)
			.exchange().expectStatus().isNotModified.expectBody().isEmpty()

		joinGame(signIn("etag-guest@example.com", "Grace", "Grace Freight"), game.id).expectStatus().isOk
		restTestClient.get().uri("/api/games/{id}", game.id).header("Authorization", "Bearer $token").header("If-None-Match", etag)
			.exchange().expectStatus().isOk.expectHeader().value("ETag") { assertThat(it).isNotEqualTo(etag) }
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
		gameStore.create(
			Game("started-game", 20, GameStatus.RUNNING, host.email, listOf(host), emptyList(), null, MarketPrices.board(), emptyList(), emptyList())
		)

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
		assertThat(seenByCreator.players.map { it.id }).containsExactly("p1", "p2")
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
		assertThat(
			started.turn
		).isEqualTo(
			TurnResponseDto(
				1, "BIDDING",
				emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), emptyList(), null, emptyList(), emptyList(),
				null, null, emptyList(), emptyList(), emptyList(), null, emptyList(), null, emptyList()
			)
		)
		assertThat(started.you!!.money).isEqualTo(300)
		assertThat(started.you!!.ships).hasSize(2).allMatch { it.cargo.isEmpty() && it.cargoUsed == 0 && it.cargoSpaces == 8 }
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
		assertThat(started.log.first()).isEqualTo(LogEntryResponseDto(1, "Game started: 2 players, 20 turns", null, null, null))
		joinGame(signIn("start-late@example.com"), game.id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game has already started")
	}

	@Test
	fun shouldGateTurnOneBiddingOnPlacingTheStartingConnectionPoints() {
		// R82, E12-S01: 3 + d3 points each, public, placed once in turn 1's Bidding before done
		val creatorToken = signIn("connections-host@example.com")
		val joinerToken = signIn("connections-guest@example.com", "Grace", "Grace Freight")
		val game = createdGame(creatorToken)
		joinGame(joinerToken, game.id).expectStatus().isOk
		val started = startGame(creatorToken, game.id).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val points = started.players.first().connectionPoints
		assertThat(points).isBetween(4, 6)
		assertThat(started.players.first().connections).isEqualTo(ConnectionsResponseDto(0, 0, 0))

		signalDone(creatorToken, game.id)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Place your $points connection points first.")
		listOf(
			"""{"political": 11, "business": 0, "criminal": 0}""" to "Enter a whole number from 0 to 10 for each connection.",
			"""{"political": 1.5, "business": 0, "criminal": 0}""" to "Enter a whole number from 0 to 10 for each connection.",
			"""{"political": 1, "business": 0}""" to "Enter a whole number from 0 to 10 for each connection.",
			"""{"political": 1, "business": 0, "criminal": 0}""" to "Your connections must add up to $points points."
		).forEach { (body, message) ->
			placement(creatorToken, game.id, body).expectStatus().isBadRequest.expectBody(String::class.java).isEqualTo(message)
		}
		val placed = placement(creatorToken, game.id, """{"political": 2, "business": ${points - 2}, "criminal": 0}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(placed.players.first().connections).isEqualTo(ConnectionsResponseDto(2, points - 2, 0))
		assertThat(placed.players.first().connectionPoints).isZero()
		assertThat(placed.log.last().text).isEqualTo("Adrian Star Traders's connections: political 2, business ${points - 2}, criminal 0")
		placement(creatorToken, game.id, """{"political": 2, "business": ${points - 2}, "criminal": 0}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("You have already placed your connection points.")
		signalDone(creatorToken, game.id).expectStatus().isOk
		placement(null, game.id, "{}").expectStatus().isUnauthorized
		placement(signIn("connections-stranger@example.com"), game.id, "{}").expectStatus().isForbidden
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
		assertThat(raw).contains("\"you\":{\"id\":\"p2\",\"money\":300,")
	}

	@Test
	fun shouldNeverSendAnyPlayersEmail() {
		// A player is named by their place in join order; an email address is theirs alone
		val creatorToken = signIn("no-email-host@example.com")
		val joinerToken = signIn("no-email-guest@example.com", "Grace", "Grace Freight")
		val game = createdGame(creatorToken)
		joinGame(joinerToken, game.id).expectStatus().isOk
		val lobby = pollGame(joinerToken, game.id).expectBody(String::class.java).returnResult().responseBody!!
		startGame(creatorToken, game.id).expectStatus().isOk
		placeConnections(creatorToken, game.id)
		placeConnections(joinerToken, game.id)
		signalDone(creatorToken, game.id).expectStatus().isOk
		signalDone(joinerToken, game.id).expectStatus().isOk

		val running = pollGame(joinerToken, game.id).expectStatus().isOk.expectBody(String::class.java).returnResult().responseBody!!
		val seen = pollGame(joinerToken, game.id).expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(lobby).doesNotContain("@")
		assertThat(running).doesNotContain("@")
		assertThat(seen.creatorId).isEqualTo("p1")
		assertThat(seen.players.map { it.id }).containsExactly("p1", "p2")
		assertThat(seen.you!!.id).isEqualTo("p2")
		assertThat(seen.turn!!.initiative).containsExactlyInAnyOrder("p1", "p2")
	}

	@Test
	fun shouldNeverSendAnotherPlayersHullsModulesOrStats() {
		// E07-S03-6, R57, engineering standards §10: the joiner's first ship is a Flute with an Arsenal and a Passenger module
		val (creatorToken, joinerToken, gameId) = startedGame("fleet")
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != "fleet-guest@example.com") {
						player
					} else {
						val flute = player.ships[0].copy(hull = Hull.FLUTE, modules = listOf(Module.ARSENAL, Module.PASSENGER))
						player.copy(ships = listOf(flute) + player.ships.drop(1))
					}
				}
			)
		}

		val seenByCreator = pollGame(creatorToken, gameId).expectStatus().isOk
			.expectBody(String::class.java).returnResult().responseBody!!
		val seenByJoiner = pollGame(joinerToken, gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(seenByCreator).doesNotContain("Flute").doesNotContain("Arsenal").doesNotContain("Passenger")
		// the creator's own two ships, and nobody else's
		assertThat(Regex("\"hull\"").findAll(seenByCreator).count()).isEqualTo(2)
		assertThat(Regex("\"modules\"").findAll(seenByCreator).count()).isEqualTo(2)
		assertThat(Regex("\"stats\"").findAll(seenByCreator).count()).isEqualTo(2)
		val flute = seenByJoiner.you!!.ships.first()
		assertThat(flute.hull).isEqualTo(HullResponseDto("Flute", military = false, aerodynamic = true))
		assertThat(flute.modules.map { it.name to it.count }).containsExactly("Arsenal" to 1, "Passenger" to 1)
		assertThat(flute.stats.crewNeeded).isEqualTo(3)
		assertThat(flute.stats.passengers).isEqualTo(2)
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
		).isEqualTo(
			TurnResponseDto(
				1, "BIDDING",
				listOf(
					"p2"
				),
				emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), emptyList(), null, emptyList(), emptyList(), null, null,
				emptyList(), emptyList(), emptyList(), null, emptyList(), null, emptyList()
			)
		)

		signalDone(creatorToken, gameId).expectStatus().isOk

		val polled = pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(polled.turn!!.phase).isEqualTo("INITIATIVE")
		assertThat(polled.turn!!.doneBidding).isEmpty()
		assertThat(polled.turn!!.initiative).containsExactlyInAnyOrder("p1", "p2")
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
	fun shouldWithdrawAnOfferDuringBiddingAndLetThePlayerOfferAgain() {
		val (_, joinerToken, gameId) = startedGame("withdraw")
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}""").expectStatus().isCreated
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "ISO", "type": "SELL", "price": 9}""").expectStatus().isCreated

		val withdrawn = withdrawOffer(joinerToken, gameId, "TAU_CETI", "POLY")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(withdrawn.you!!.offers).containsExactly(YourOfferResponseDto("TAU_CETI", "Tau Ceti", "ISO", "SELL", 9))
		// R59: a second withdrawal (a double click) changes nothing.
		withdrawOffer(joinerToken, gameId, "TAU_CETI", "POLY").expectStatus().isOk
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "SELL", "price": 12}""").expectStatus().isCreated
		assertThat(gameStore.find(gameId)!!.turn!!.offers).hasSize(2)
	}

	@Test
	fun shouldRefuseAWithdrawalAfterBiddingClosedOrForAnUnknownGoodOrSystem() {
		val (creatorToken, joinerToken, gameId) = startedGame("withdraw-late")
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}""").expectStatus().isCreated

		withdrawOffer(joinerToken, gameId, "NOWHERE", "POLY")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose a star system.")
		withdrawOffer(joinerToken, gameId, "TAU_CETI", "GOLD")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose a good.")
		withdrawOffer(signIn("withdraw-stranger@example.com"), gameId, "TAU_CETI", "POLY").expectStatus().isForbidden
		withdrawOffer(null, gameId, "TAU_CETI", "POLY").expectStatus().isUnauthorized

		signalDone(joinerToken, gameId).expectStatus().isOk
		signalDone(creatorToken, gameId).expectStatus().isOk
		withdrawOffer(joinerToken, gameId, "TAU_CETI", "POLY")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Bidding has closed")
		assertThat(gameStore.find(gameId)!!.turn!!.offers).hasSize(1)
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
	fun shouldNeverSendAnotherPlayersBidWhileBiddingIsOpenNorTheirMoney() {
		val (creatorToken, joinerToken, gameId) = startedGame("bid-secret")
		placeBid(joinerToken, gameId, """{"amount": 137}""").expectStatus().isOk
		signalDone(joinerToken, gameId).expectStatus().isOk

		// Every response the creator can get while Bidding is open: the poll and their own bid (R29, S05-6).
		val whileOpen = listOf(pollGame(creatorToken, gameId), placeBid(creatorToken, gameId, """{"amount": 5}"""))
			.map { it.expectBody(String::class.java).returnResult().responseBody!! }
		// Then the done that closes Bidding and the poll after: every bid is public now (creator, 2026-11-18), money still isn't.
		val afterClose = listOf(signalDone(creatorToken, gameId), pollGame(creatorToken, gameId))
			.map { it.expectBody(String::class.java).returnResult().responseBody!! }

		whileOpen.forEach { raw ->
			assertThat(raw).doesNotContain("\"bid\":137").doesNotContain("\"amount\"")
			assertThat(Regex("\"bid\"").findAll(raw).count()).isEqualTo(1) // only "you.bid"
		}
		(whileOpen + afterClose).forEach { raw ->
			assertThat(raw).doesNotContain("\"money\":163")
			assertThat(Regex("\"money\"").findAll(raw).count()).isEqualTo(1) // only "you.money"
		}
		afterClose.forEach { raw -> assertThat(raw).contains("{\"playerId\":\"p2\",\"bid\":137,") }
		assertThat(afterClose.last()).contains("\"money\":295")
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
		assertThat(closed.turn!!.initiative).containsExactlyInAnyOrder("p1", "p2")
		val logged = closed.log.last()
		assertThat(logged.turn).isEqualTo(1)
		// Once Bidding closes, everyone is told what each player bid and rolled (creator, 2026-11-18).
		assertThat(logged.text).startsWith("Initiative (bid + 2d6): ").contains("Grace Freight 40 + ", "Adrian Star Traders 10 + ")
		assertThat(closed.turn!!.initiativeRolls.map { it.playerId to it.bid }).containsExactlyInAnyOrder("p1" to 10, "p2" to 40)
		assertThat(closed.turn!!.initiativeRolls).allMatch { it.total == it.bid + it.roll && it.roll in 2..12 }
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

		// S06-1: every entry carries its turn; S06-3: none names an offer or money (R17, R29). Bids are public once Bidding
		// has closed (creator, 2026-11-18), so the initiative line names the 23.
		assertThat(
			raw
		).contains("\"log\":[{\"turn\":1,\"text\":\"Game started: 2 players, 20 turns\",\"trade\":null,\"notice\":null,\"battleSummary\":null}")
		assertThat(log.map { it.turn }).containsOnly(1)
		// 2 at the start, a roll and a placement for each player (R82), and the initiative line
		assertThat(log.map { it.text }).hasSize(7).noneMatch { text ->
			listOf("17", "277", "HT", "POLY", "SELL").any { it in text }
		}
		assertThat(log.last().text).contains("Grace Freight 23 + ")
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
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative

		// E04-S01: the first in initiative order may take last place; the other player gets the place left (S01-6).
		val picked = pickPlace("token-$first", gameId, """{"place": 2}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(picked.turn!!.turnOrder).containsExactly(idOf(gameId, second), idOf(gameId, first))
		assertThat(picked.turn!!.phase).isEqualTo("NEWS")
		assertThat(picked.log.map { it.text }).anyMatch { it.startsWith("Turn order (for this turn): ") }
		assertThat(pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.turn!!.turnOrder)
			.containsExactly(idOf(gameId, second), idOf(gameId, first))
	}

	@Test
	fun shouldRefuseAPickOutOfTurnOrForAPlaceThatIsNotFree() {
		val (creatorToken, joinerToken, gameId) = startedGame("bad-pick")
		pickPlace(joinerToken, gameId, """{"place": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Initiative hasn't started.")
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative
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
			.expectBody(String::class.java).isEqualTo("Initiative has closed")
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
			.players.first { it.id == idOf(gameId, first) }.ships.first()
		val destination = certainDestination("token-$first", gameId, ship.name)

		// E04-S02: the first place jumps one ship; it arrives at once, in space, and the jump is public.
		val jumped = jumpShip("token-$first", gameId, """{"ship": "${ship.name}", "destination": "${destination.name}"}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val seenByOther = pollGame("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(seenByOther.players.first { it.id == idOf(gameId, first) }.ships.first())
			.isEqualTo(ShipResponseDto(ship.name, destination.name, destination.displayName, "SPACE", 0))
		assertThat(seenByOther.turn!!.jumpedShips).containsExactly(ship.name)
		assertThat(jumped.log.last().text).matches(".*— \\Q${ship.name} → ${destination.displayName}\\E \\(rolled \\d+\\)")

		// E04-S03: the jump finishes once the player chooses where the ship ends up.
		assertThat(seenByOther.turn!!.landingShip).isEqualTo(ship.name)
		finishJumping("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Choose where ${ship.name} ends up first.")
		val landed = landShip("token-$first", gameId, """{"location": "SPACE"}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(landed.players.first { it.id == idOf(gameId, first) }.ships.first().location).isEqualTo("SPACE")
		assertThat(landed.turn!!.landingShip).isNull()
		assertThat(landed.log.last().text).endsWith("— ${ship.name} stays in space (${destination.displayName})")

		finishJumping("token-$first", gameId).expectStatus().isOk
		val trading = finishJumping("token-$second", gameId)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(trading.turn!!.phase).isEqualTo("TRADING")
		assertThat(trading.turn!!.doneJumping).containsExactly(idOf(gameId, first), idOf(gameId, second))
	}

	@Test
	fun shouldNeverShowAnotherPlayersCargoAfterAJump() {
		val (first, second, gameId) = orderedGame("jump-cargo")
		val ship = pollGame("token-$first", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
			.players.first { it.id == idOf(gameId, first) }.ships.first()
		val destination = certainDestination("token-$first", gameId, ship.name)
		jumpShip("token-$first", gameId, """{"ship": "${ship.name}", "destination": "${destination.name}"}""").expectStatus().isOk

		// S02-10, R29: where the ship is, yes; what it carries, no.
		pollGame("token-$second", gameId).expectBody()
			.jsonPath("$.players[*].ships[*].cargo").doesNotExist()
			.jsonPath("$.players[*].money").doesNotExist()
	}

	@Test
	fun shouldKeepShowingAPlayerTheirOwnOffersThroughShipJumps() {
		val (creatorToken, joinerToken, gameId) = startedGame("offers-jumps")
		placeOffer(joinerToken, gameId, """{"system": "TAU_CETI", "goods": "POLY", "type": "BUY", "price": 8}""").expectStatus().isCreated
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val first = gameStore.find(gameId)!!.turn!!.initiative.first()
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		val ship = closed.players.first { it.id == idOf(gameId, first) }.ships.first()
		val destination = certainDestination("token-$first", gameId, ship.name)
		jumpShip("token-$first", gameId, """{"ship": "${ship.name}", "destination": "${destination.name}"}""").expectStatus().isOk
		landShip("token-$first", gameId, """{"location": "PORT"}""").expectStatus().isOk

		// E04-S04-2: the offer stays its owner's, under "Your next jump", and still nobody else's (R17).
		assertThat(pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.offers)
			.containsExactly(YourOfferResponseDto("TAU_CETI", "Tau Ceti", "POLY", "BUY", 8))
		assertThat(pollGame(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.offers)
			.isEmpty()
	}

	@Test
	fun shouldRefuseAJumpOutOfTurnOrThatBreaksAJumpRule() {
		val (creatorToken, joinerToken, gameId) = startedGame("bad-jump")
		jumpShip(joinerToken, gameId, """{"ship": "Lantern", "destination": "TAU_CETI"}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Jumps hasn't started.")
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative
		finishJumping("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Jumps hasn't started.")
		val ordered = pickPlace("token-$first", gameId, """{"place": 1}""").expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val firstName = if (first == "bad-jump-guest@example.com") "Grace Freight" else "Adrian Star Traders"
		val ship = ordered.players.first { it.id == idOf(gameId, first) }.ships.first()
		val otherShip = ordered.players.first { it.id == idOf(gameId, second) }.ships.first()

		finishJumping("token-$second", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("It's $firstName's turn to jump.")
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
			.players.first { it.id == idOf(gameId, first) }.ships.first()
		val destination = certainDestination("token-$first", gameId, ship.name)
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

	@Test
	fun shouldLetTheFirstInInitiativeOrderAndResolveTheGoods() {
		val (creatorToken, joinerToken, gameId) = startedGame("trade")
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		finishJumping("token-$first", gameId).expectStatus().isOk
		val trading = finishJumping("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val firstCorporation = trading.players.first { it.id == idOf(gameId, first) }.corporationName

		// S01-2: every good at every system, by system in map order, then good; public, so the second player sees it too.
		val queue = trading.turn!!.tradingQueue
		assertThat(queue).hasSize(18)
		assertThat(queue.first()).isEqualTo(MarketGoodResponseDto("GAMMA_LEPORIS", "Gamma Leporis", "POLY"))
		assertThat(pollGame("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.turn!!.tradingQueue)
			.isEqualTo(queue)

		// R25: only the first in initiative orders and resolves.
		resolveNext("token-$second", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Order decided by $firstCorporation (first in initiative).")
		val reversed = queue.reversed()
		val body = """{"queue": [${reversed.joinToString(", ") { """{"system": "${it.system}", "goods": "${it.goods}"}""" }}]}"""
		reorderQueue("token-$second", gameId, body).expectStatus().isEqualTo(HttpStatus.CONFLICT)
		val reordered = reorderQueue("token-$first", gameId, body)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(reordered.turn!!.tradingQueue).isEqualTo(reversed)

		// S01-5: each resolution is public and logged.
		val resolved = resolveNext("token-$first", gameId)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(resolved.turn!!.resolvedGoods).containsExactly(reversed.first())
		assertThat(resolved.turn!!.tradingQueue).isEqualTo(reversed.drop(1))
		// S03-7: with no offers, whatever the roll, nobody can trade. E05-S05-2: the price still moves, from 8 HT.
		assertThat(resolved.log.takeLast(5).map { it.text }).startsWith(
			"Resolving: Sigma Draconis POLY (chosen by $firstCorporation)",
			"No offers for Sigma Draconis POLY"
		).contains("No one can trade at Sigma Draconis POLY")
		assertThat(resolved.log.last().text).matches("POLY price at Sigma Draconis: 8 → \\d+ HT")

		// A04: the old order still names the good that just resolved, so it's stale.
		reorderQueue("token-$first", gameId, body)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("The remaining goods have changed. Try again.")
	}

	@Test
	fun shouldStartTheNextTurnWithBiddingOnceEveryoneIsDoneInvesting() {
		// E05-S07, E06-S01: with no offers and no positions, each good resolves on Resolve next; the last one starts Investment.
		val (first, second, gameId) = orderedGame("turn-end")
		finishJumping("token-$first", gameId).expectStatus().isOk
		finishJumping("token-$second", gameId).expectStatus().isOk
		repeat(17) { resolveNext("token-$first", gameId).expectStatus().isOk }

		val opportunity = resolveNext("token-$first", gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(opportunity.turn!!.number).isEqualTo(1)
		assertThat(opportunity.turn!!.phase).isEqualTo("OPPORTUNITY")
		assertThat(opportunity.turn!!.opportunist).isEqualTo(idOf(gameId, first))
		assertThat(opportunity.log.last().text).isEqualTo("All goods resolved")

		// R102: one at a time in the turn order; the last one done opens Investment.
		finishOpportunities("token-$second", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo(
				"It's ${gameStore.find(gameId)!!.players.first {
					it.email == first
				}.corporationName}'s Opportunity turn."
			)
		finishOpportunities("token-$first", gameId).expectStatus().isOk
		val investing = finishOpportunities("token-$second", gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(investing.turn!!.phase).isEqualTo("INVESTMENT")
		assertThat(investing.turn!!.doneInvesting).isEmpty()

		// E06-S01-2, S01-3: one player's signal shows to the other, and the last one's starts the next turn.
		val oneDone = signalDoneInvesting("token-$first", gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(oneDone.turn!!.phase).isEqualTo("INVESTMENT")
		assertThat(oneDone.turn!!.doneInvesting).hasSize(1)
		assertThat(pollGame("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.turn!!.doneInvesting)
			.isEqualTo(oneDone.turn!!.doneInvesting)
		val ended = signalDoneInvesting("token-$second", gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		// S07-2: turn 2's Bidding, with nothing of turn 1's Trading left; saved, so the other player's poll sees it too.
		assertThat(ended.status).isEqualTo("RUNNING")
		assertThat(ended.turn!!.number).isEqualTo(2)
		assertThat(ended.turn!!.phase).isEqualTo("BIDDING")
		assertThat(ended.turn!!.tradingQueue).isEmpty()
		assertThat(ended.turn!!.resolution).isNull()
		assertThat(pollGame("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.turn)
			.isEqualTo(ended.turn)
		// Creator, 2026-11-19: no line announces turn 2; the log's last line is turn 1's.
		assertThat(ended.log.last()).isEqualTo(LogEntryResponseDto(1, "All goods resolved", null, null, null))
		resolveNext("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Trading hasn't started.")
		// A04: a signal that arrives after Investment closed is refused.
		signalDoneInvesting("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Investment isn't open.")
	}

	@Test
	fun shouldRefuseDoneInvestingFromOutsidersOrOutsideInvestment() {
		val (first, _, gameId) = orderedGame("investing-refused")

		signalDoneInvesting("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Investment isn't open.")
		signalDoneInvesting(null, gameId).expectStatus().isUnauthorized
		signalDoneInvesting(signIn("investing-stranger@example.com"), gameId).expectStatus().isForbidden
		signalDoneInvesting("token-$first", "no-such-game").expectStatus().isNotFound
	}

	/**
	 * A two-player game at the start of turn 1's Opportunity where the first in initiative owns a 6-unit ISO factory where
	 * their first ship is in port (nothing can build one yet, E06-S04, S05), so the test seeds it. Returns their token,
	 * the other player's token, the game id and that system's constant name.
	 */
	private fun investingWithAFactory(prefix: String): InvestingGame {
		val (first, second, gameId) = orderedGame(prefix)
		finishJumping("token-$first", gameId).expectStatus().isOk
		finishJumping("token-$second", gameId).expectStatus().isOk
		repeat(18) { resolveNext("token-$first", gameId).expectStatus().isOk }
		val updated = gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != first) {
						player
					} else {
						// Tau Ceti both makes and trades ISO (R46), as every real factory's system does.
						// The other ship goes elsewhere: starting systems are random, and it could have started at Tau Ceti too.
						val atTau = player.ships[0].copy(system = StarSystem.TAU_CETI)
						val elsewhere = player.ships.drop(1).map { it.copy(system = StarSystem.MU_HERCULIS) }
						player.copy(ships = listOf(atTau) + elsewhere, factories = listOf(Factory(StarSystem.TAU_CETI, Goods.ISO, 6, 0)))
					}
				}
			)
		}
		return InvestingGame("token-$first", "token-$second", gameId, updated.players.first { it.email == first }.ships[0])
	}

	private data class InvestingGame(val owner: String, val other: String, val gameId: String, val ship: Ship)

	/** [investingWithAFactory] taken on to Investment: the owner collected nothing, and both were done with opportunities. */
	private fun investingWithAFactoryCollected(prefix: String): InvestingGame {
		val game = investingWithAFactory(prefix)
		passOpportunity(game.gameId)
		return game
	}

	@Test
	fun shouldCollectOutputIntoAShipAndShowTheCargoOnlyToItsPlayer() {
		// E06-S03-1, S03-5, A05: the owner's cargo is private; everyone sees only that they collected
		val (owner, other, gameId, ship) = investingWithAFactory("collect")
		val before = pollGame(owner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(before.you!!.collect!!.factories).hasSize(1)
		assertThat(before.you!!.collect!!.factories[0].destinations).containsExactly(CollectDestinationResponseDto(ship.name, 8))

		val body = """{"placements": [{"system": "${ship.system.name}", "goods": "ISO", "ship": "${ship.name}", "units": 4}]}"""
		val collected = collectOutput(owner, gameId, body).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(collected.turn!!.collected).hasSize(1)
		assertThat(collected.you!!.collect).isNull()
		assertThat(collected.you!!.ships.first { it.name == ship.name }.cargo).containsEntry("ISO", 4)
		val seenByOther = pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(seenByOther.turn!!.collected).isEqualTo(collected.turn!!.collected)
		assertThat(seenByOther.you!!.collect).isNull()
		assertThat(seenByOther.you!!.ships.flatMap { it.cargo.keys }).isEmpty()
		val raw = pollGame(other, gameId).expectBody(String::class.java).returnResult().responseBody!!
		assertThat(raw).doesNotContain("\"ISO\":4")
		// E06-S07-1, R50: what a factory's output came to is public, in the log
		assertThat(seenByOther.log.last().text).endsWith(" collected 4 u of ISO at ${ship.system.displayName} (2 lost)")
	}

	@Test
	fun shouldOfferTheCollectToTheFactoryOwnerOnly() {
		// E06-S03-6: a player with no factories has nothing to collect and goes straight on
		val (owner, other, gameId, _) = investingWithAFactory("collect-offer")

		assertThat(pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.collect).isNull()
		assertThat(pollGame(owner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.collect).isNotNull()
		collectOutput(other, gameId, "{}")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("You have no factories to collect from.")
		collectOutput(owner, gameId, "{}").expectStatus().isOk
		finishOpportunities(owner, gameId).expectStatus().isOk
		finishOpportunities(other, gameId).expectStatus().isOk
	}

	@Test
	fun shouldNotLetTheOwnerSignalDoneBeforeTheyCollect() {
		// E06-S03-5, S03-8
		val (owner, _, gameId, _) = investingWithAFactory("collect-first")

		finishOpportunities(owner, gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Collect your output first.")
		collectOutput(owner, gameId, "{}").expectStatus().isOk
		finishOpportunities(owner, gameId).expectStatus().isOk
	}

	@Test
	fun shouldRefuseACollectTheRulesRefuseAndOneThatIsRepeated() {
		val (owner, _, gameId, ship) = investingWithAFactory("collect-refused")

		collectOutput(
			owner,
			gameId,
			"""{"placements": [{"system": "${ship.system.name}", "goods": "ISO", "ship": "${ship.name}", "units": 7}]}"""
		)
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Your ISO factory at ${ship.system.displayName} makes 6 units.")
		collectOutput(
			owner,
			gameId,
			"""{"placements": [{"system": "${ship.system.name}", "goods": "ISO", "ship": "No Such Ship", "units": 1}]}"""
		)
			.expectStatus().isBadRequest
		collectOutput(owner, gameId, """{"placements": [{"system": "${ship.system.name}", "goods": "ISO", "units": 1}]}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("You have no warehouse at ${ship.system.displayName}.")
		collectOutput(owner, gameId, """{"placements": [{"system": "NOWHERE", "goods": "ISO", "ship": "${ship.name}", "units": 1}]}""")
			.expectStatus().isBadRequest
		collectOutput(
			owner,
			gameId,
			"""{"placements": [{"system": "${ship.system.name}", "goods": "ISO", "ship": "${ship.name}", "units": 2.5}]}"""
		)
			.expectStatus().isBadRequest
		assertThat(gameStore.find(gameId)!!.turn!!.collected).isEmpty()

		collectOutput(owner, gameId, "{}").expectStatus().isOk
		collectOutput(owner, gameId, "{}")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("You have already collected your output.")
	}

	@Test
	fun shouldBuyWarehouseBlocksAndShowTheLogLineButNotThePrice() {
		// E06-S04-1, S04-3, S04-5, R50: a player with no factories goes straight to buying
		val (owner, other, gameId, _) = investingWithAFactoryCollected("blocks")
		val otherEmail = other.removePrefix("token-")
		val corporation = gameStore.find(gameId)!!.players.first { it.email == otherEmail }.corporationName
		val before = pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(before.you!!.invest!!.blockCost).isEqualTo(20)
		val system = before.you!!.invest!!.systems.first()
		assertThat(system.blocks).isZero()

		val bought = buyBlocks(other, gameId, """{"system": "${system.system}", "blocks": 2}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(bought.you!!.money).isEqualTo(before.you!!.money - 40)
		assertThat(bought.you!!.warehouses.single { it.system == system.system }.blocks).isEqualTo(2)
		assertThat(bought.you!!.invest!!.systems.first { it.system == system.system }.blocks).isEqualTo(2)
		assertThat(bought.log.last().text).isEqualTo("$corporation added 2 warehouse blocks at ${system.systemName}")
		assertThat(bought.log.last().text).doesNotContain("HT")
		val seenByOwner = pollGame(owner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(seenByOwner.players.single { it.id == idOf(gameId, otherEmail) }.warehouses.single().blocks).isEqualTo(2)
		assertThat(seenByOwner.you!!.invest).isNotNull()
	}

	@Test
	fun shouldRecruitAnAgentShowingOthersOnlyWhereItIsThenBorrowPrivately() {
		// R103, R105, R102, R90: paying 60 HT always recruits; the other player sees a count aboard the ship, never the agent
		val (owner, other, gameId, ship) = investingWithAFactory("agents")
		collectOutput(owner, gameId, "{}").expectStatus().isOk
		val recruited = restTestClient.post().uri("/api/games/{id}/agents/recruit", gameId).header("Authorization", "Bearer $owner")
			.contentType(MediaType.APPLICATION_JSON).body("""{"amount": 60, "ship": "${ship.name}"}""").exchange()
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val agent = recruited.you!!.agents.single()
		assertThat(agent.ship).isEqualTo(ship.name)

		val seenByOther = pollGame(other, gameId).expectBody(String::class.java).returnResult().responseBody!!
		assertThat(
			seenByOther
		).contains("\"name\":\"${ship.name}\",\"system\":\"TAU_CETI\",\"systemName\":\"Tau Ceti\",\"location\":\"PORT\",\"agents\":1")
		assertThat(seenByOther).doesNotContain(agent.effect).doesNotContain("\"agent\":\"${agent.agent}\"")

		finishOpportunities(owner, gameId).expectStatus().isOk
		finishOpportunities(other, gameId).expectStatus().isOk
		val borrowed = restTestClient.post().uri("/api/games/{id}/loan", gameId).header("Authorization", "Bearer $owner")
			.contentType(MediaType.APPLICATION_JSON).body("""{"amount": 100, "term": 8}""").exchange()
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(borrowed.you!!.loan!!.loan!!.amount).isEqualTo(100)
		assertThat(borrowed.you!!.loan!!.loan!!.interest).isEqualTo(8)
		assertThat(pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!.loan!!.loan).isNull()
		restTestClient.post().uri("/api/games/{id}/loan", gameId).header("Authorization", "Bearer $owner")
			.contentType(MediaType.APPLICATION_JSON).body("""{"amount": 10, "term": 5}""").exchange()
			.expectStatus().isBadRequest.expectBody(String::class.java).isEqualTo("You already have a loan; repay it before taking another.")
	}

	@Test
	fun shouldOfferNothingToBuyInOpportunity() {
		// E06-S04-1, R102: buying happens only in Investment, so no warehouse catches output or cargo in Opportunity
		val (owner, _, gameId, ship) = investingWithAFactory("blocks-collect-first")

		buyBlocks(owner, gameId, """{"system": "${ship.system.name}", "blocks": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Investment isn't open.")
		passOpportunity(gameId)
		val after = pollGame(owner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(after.you!!.invest!!.systems.map { it.system }).contains(ship.system.name)
		buyBlocks(owner, gameId, """{"system": "${ship.system.name}", "blocks": 1}""").expectStatus().isOk
	}

	@Test
	fun shouldRefuseABuyTheRulesRefuse() {
		// E06-S04-2: too little money, a missing or fractional amount, an unknown system (no ship is needed)
		val (_, other, gameId, _) = investingWithAFactoryCollected("blocks-refused")
		val otherEmail = other.removePrefix("token-")
		val home = gameStore.find(gameId)!!.players.first { it.email == otherEmail }.ships.first().system
		buyBlocks(other, gameId, """{"system": "${home.name}", "blocks": 16}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("16 blocks cost 320 HT; you have 300 HT.")
		buyBlocks(other, gameId, """{"system": "${home.name}", "blocks": 0}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Enter a whole number of blocks, 1 or more.")
		buyBlocks(other, gameId, """{"system": "${home.name}", "blocks": 1.5}""").expectStatus().isBadRequest
		buyBlocks(other, gameId, """{"system": "${home.name}"}""").expectStatus().isBadRequest
		buyBlocks(other, gameId, """{"system": "NOWHERE", "blocks": 1}""").expectStatus().isBadRequest
		buyBlocks(other, gameId, "{}").expectStatus().isBadRequest
		assertThat(gameStore.find(gameId)!!.players.first { it.email == otherEmail }.money).isEqualTo(300)
	}

	@Test
	fun shouldBuyWarehouseBlocksAtASystemWhereThePlayerHasNoShip() {
		// creator, 2026-11-16: investing needs no ship
		val (_, other, gameId) = investingAtTau("blocks-noship")
		val otherEmail = other.removePrefix("token-")

		val bought = buyBlocks(other, gameId, """{"system": "SIGMA_DRACONIS", "blocks": 2}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(bought.you!!.invest!!.systems.single { it.system == "SIGMA_DRACONIS" }.blocks).isEqualTo(2)
		assertThat(bought.players.single { it.id == idOf(gameId, otherEmail) }.warehouses.single().blocks).isEqualTo(2)
	}

	@Test
	fun shouldRefuseABuyFromOutsidersOrOutsideInvestment() {
		val (first, _, gameId) = orderedGame("blocks-outside")
		val system = gameStore.find(gameId)!!.players.first { it.email == first }.ships.first().system.name

		buyBlocks("token-$first", gameId, """{"system": "$system", "blocks": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Investment isn't open.")
		buyBlocks(null, gameId, "{}").expectStatus().isUnauthorized
		buyBlocks(signIn("blocks-stranger@example.com"), gameId, """{"system": "$system", "blocks": 1}""").expectStatus().isForbidden
		buyBlocks("token-$first", "no-such-game", """{"system": "$system", "blocks": 1}""").expectStatus().isNotFound
	}

	/**
	 * A two-player game at the start of turn 1's Investment where the first in initiative has a ship at Tau Ceti and an ISO
	 * factory of 6 there, and the other has a ship at Gamma Leporis and no factory (the test moves the ships, whose
	 * start is random). The owner has political 5 and business 5, room for 4 more units of factory (R84). Returns the
	 * owner's token, the other's token and the game id.
	 */
	private fun investingAtTau(prefix: String): Triple<String, String, String> {
		val (first, second, gameId) = orderedGame(prefix)
		finishJumping("token-$first", gameId).expectStatus().isOk
		finishJumping("token-$second", gameId).expectStatus().isOk
		repeat(18) { resolveNext("token-$first", gameId).expectStatus().isOk }
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					val at = if (player.email == first) StarSystem.TAU_CETI else StarSystem.GAMMA_LEPORIS
					val moved = player.copy(ships = listOf(player.ships[0].copy(system = at)) + player.ships.drop(1))
					if (player.email == first) moved.copy(factories = listOf(Factory(at, Goods.ISO, 6, 0)), connections = Connections(5, 5, 0)) else moved
				}
			)
		}
		passOpportunity(gameId)
		return Triple("token-$first", "token-$second", gameId)
	}

	@Test
	fun shouldBuyAShipIntoTheYardShowingItsHullOnlyToItsOwner() {
		// E09-S02-1, S02-4, S02-5, S02-6, R57
		val (owner, other, gameId) = investingAtTau("ship-buy")
		val otherEmail = other.removePrefix("token-")
		val corporation = gameStore.find(gameId)!!.players.first { it.email == otherEmail }.corporationName
		val before = pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val shipyard = before.you!!.invest!!.shipyard
		assertThat(shipyard.shipyards.map { it.system }).containsExactly("TAU_CETI", "EPSILON_ERIDANI", "BETA_HYDRI")
		assertThat(shipyard.hulls.map { it.id }).contains("PHOENIX").doesNotContain("SWORD", "DAGGER", "SPEAR")
		assertThat(shipyard.modules.map { it.id }).contains("CARGO", "SAFE_JUMP").doesNotContain("ARSENAL")
		assertThat(shipyard.crewClasses.map { it.id to it.pricePerCrew }).containsExactly("A" to 20, "B" to 10, "C" to 6, "D" to 2)
		assertThat(shipyard.freeNames).contains("Spiral")

		val bought = buyShip(
			other,
			gameId,
			"""{"name": "Sea Wolf", "shipyard": "TAU_CETI", "hull": "PHOENIX", "modules": ["CARGO"], "crewClass": "D"}"""
		).expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		// 85 hull + 4 Cargo + 1 crew × 2
		assertThat(bought.you!!.money).isEqualTo(before.you!!.money - 91)
		val mine = bought.you!!.ships.single { it.name == "Sea Wolf" }
		assertThat(mine.hull.name).isEqualTo("Phoenix")
		assertThat(mine.crewClass).isEqualTo("D")
		assertThat(mine.leavesYardOnTurn).isEqualTo(2)
		assertThat(mine.saleValue).isEqualTo(44)
		assertThat(bought.log.last().text).isEqualTo("$corporation ordered Sea Wolf at the Tau Ceti shipyard")
		val seenByOwner = pollGame(owner, gameId).expectBody(String::class.java).returnResult().responseBody!!
		assertThat(
			seenByOwner
		).contains("\"name\":\"Sea Wolf\",\"system\":\"TAU_CETI\",\"systemName\":\"Tau Ceti\",\"location\":\"SHIPYARD\",\"agents\":0")
		// The other player's shipyard catalogue lists every hull, so look only at what's said about Sea Wolf: no hull (R57).
		val seaWolf = Regex("\\{[^{}]*\"name\":\"Sea Wolf\"[^{}]*\\}").findAll(seenByOwner).map { it.value }.toList()
		assertThat(seaWolf).isNotEmpty().allSatisfy { assertThat(it).doesNotContain("PHOENIX").doesNotContain("hull") }
	}

	@Test
	fun shouldRefuseAShipTheRulesDoNotAllow() {
		// E09-S02-2
		val (_, other, gameId) = investingAtTau("ship-bad")
		val takenName = gameStore.find(gameId)!!.players.first().ships.first().name
		listOf(
			"""{"name": "X", "shipyard": "NOWHERE", "hull": "PHOENIX", "crewClass": "D"}""" to "Choose a shipyard.",
			"""{"name": "X", "shipyard": "TAU_CETI", "hull": "BARGE", "crewClass": "D"}""" to "Choose a hull.",
			"""{"name": "X", "shipyard": "TAU_CETI", "hull": "PHOENIX", "modules": ["TELEPORT"], "crewClass": "D"}""" to
				"Choose modules from the list.",
			"""{"name": "X", "shipyard": "TAU_CETI", "hull": "PHOENIX", "crewClass": "Z"}""" to "Choose a crew class: A, B, C or D.",
			"""{"name": "$takenName", "shipyard": "TAU_CETI", "hull": "PHOENIX", "crewClass": "D"}""" to
				"There's already a ship called $takenName in this game.",
			"""{"name": "X", "shipyard": "MU_HERCULIS", "hull": "PHOENIX", "crewClass": "D"}""" to
				"Mu Herculis has no shipyard. Choose Beta Hydri, Epsilon Eridani or Tau Ceti.",
			"""{"name": "X", "shipyard": "TAU_CETI", "hull": "SWORD", "crewClass": "D"}""" to
				"The Sword is military: it can't be bought at a shipyard.",
			"""{"name": "X", "shipyard": "TAU_CETI", "hull": "CORCO_GAMMA", "modules": ["CARGO", "CARGO", "CARGO"], "crewClass": "D"}""" to
				"A Corco Gamma holds 2 modules, not 3.",
			"""{"name": "X", "shipyard": "TAU_CETI", "hull": "LEVIATHAN", "modules": [${List(7) {
				"\"PASSENGER\""
			}.joinToString()}], "crewClass": "A"}""" to
				"This ship costs 575 HT; you have 300 HT." // 250 + 7 × 15 + (4 + 7) crew × 20
		).forEach { (body, message) ->
			buyShip(other, gameId, body).expectStatus().isBadRequest.expectBody(String::class.java).isEqualTo(message)
		}
	}

	@Test
	fun shouldRefitAShipInTheYardAndSellIt() {
		// E09-S04-1, S04-2, E09-S05-1, S05-3
		val (_, other, gameId) = investingAtTau("ship-refit")
		val otherEmail = other.removePrefix("token-")
		val corporation = gameStore.find(gameId)!!.players.first { it.email == otherEmail }.corporationName
		val bought = buyShip(other, gameId, """{"name": "Sea Wolf", "shipyard": "BETA_HYDRI", "hull": "PHOENIX", "crewClass": "B"}""")
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		val fitted = addModule(other, gameId, "Sea Wolf", """{"module": "PASSENGER"}""")
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(fitted.you!!.money).isEqualTo(bought.you!!.money - 25) // 15 + 1 crew × 10
		assertThat(fitted.you!!.ships.single { it.name == "Sea Wolf" }.modules.map { it.id }).containsExactly("PASSENGER")

		val removed = removeModule(other, gameId, "Sea Wolf", "PASSENGER")
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(removed.you!!.money).isEqualTo(fitted.you!!.money + 7) // half of 15, rounded down

		val sold = sellShip(other, gameId, "Sea Wolf").expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(sold.you!!.money).isEqualTo(removed.you!!.money + 42) // half of 85
		assertThat(sold.you!!.ships.map { it.name }).doesNotContain("Sea Wolf")
		assertThat(sold.log.last().text).isEqualTo("$corporation sold Sea Wolf")
	}

	@Test
	fun shouldStoreAModuleInTheWarehouseAndFitItBackOverHttp() {
		// R122: the other's first ship is in port at Gamma Leporis; a one-block warehouse there takes a module for 5 spaces.
		val (_, other, gameId) = investingAtTau("module-store")
		val atGamma = gameStore.find(gameId)!!.players.first { it.email == other.removePrefix("token-") }.ships.first().name
		val bought = buyBlocks(other, gameId, """{"system": "GAMMA_LEPORIS", "blocks": 1}""")
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(bought.you!!.ships.single { it.name == atGamma }.moduleMoves.store.map { it.id }).contains("LIGHT_WEAPONS")

		val stored = storeModule(other, gameId, atGamma, "LIGHT_WEAPONS")
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val warehouse = stored.you!!.warehouses.single { it.system == "GAMMA_LEPORIS" }
		assertThat(warehouse.modules).containsExactly(StoredModuleResponseDto("LIGHT_WEAPONS", "Light weapons", 1))
		assertThat(warehouse.cargoUsed).isEqualTo(5)
		assertThat(stored.you!!.money).isEqualTo(bought.you!!.money)
		assertThat(stored.you!!.ships.single { it.name == atGamma }.moduleMoves.fit.map { it.id }).containsExactly("LIGHT_WEAPONS")

		val fitted = fitStoredModule(other, gameId, atGamma, """{"module": "LIGHT_WEAPONS"}""")
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(fitted.you!!.warehouses.single { it.system == "GAMMA_LEPORIS" }.modules).isEmpty()
		assertThat(fitted.you!!.ships.single { it.name == atGamma }.modules.map { it.id }).contains("LIGHT_WEAPONS")
		storeModule(other, gameId, atGamma, "WARP").expectStatus().isBadRequest
		moveModule(other, gameId, atGamma, "LIGHT_WEAPONS", """{"ship": "$atGamma"}""").expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose two different ships.")
	}

	@Test
	fun shouldBuyReputationOncePerInvestment() {
		// E13-S03 (R87): 20 HT for 1d6; a second buy in the same Investment is a 409.
		val (_, other, gameId) = investingAtTau("reputation-buy")
		val before = gameStore.find(gameId)!!.players.first { it.email == other.removePrefix("token-") }.money

		val bought = buyReputation(other, gameId).expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val mine = bought.players.single { it.id == bought.you!!.id }
		assertThat(bought.you!!.money).isEqualTo(before - 20)
		assertThat(mine.reputation).isBetween(21, 26)
		assertThat(bought.you!!.invest!!.boughtReputation).isTrue()
		assertThat(bought.log.last().text).contains("reputation: +").contains("for buying reputation").doesNotContain("HT")

		buyReputation(other, gameId).expectStatus().isEqualTo(409)
			.expectBody(String::class.java).isEqualTo("You've already bought reputation this Investment.")
	}

	@Test
	fun shouldReplaceACrewAndRefuseTheClassItAlreadyHas() {
		// R89: the other's first ship is a starting Clarinet (class B, 1 crew) in port; a class A crew costs 20 HT.
		val (_, other, gameId) = investingAtTau("crew-replace")
		val ship = gameStore.find(gameId)!!.players.first { it.email == other.removePrefix("token-") }.ships.first().name
		val before = gameStore.find(gameId)!!.players.first { it.email == other.removePrefix("token-") }.money

		val replaced = replaceCrew(other, gameId, ship, """{"crewClass": "A"}""")
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(replaced.you!!.money).isEqualTo(before - 20)
		assertThat(replaced.you!!.ships.single { it.name == ship }.crewClass).isEqualTo("A")

		replaceCrew(other, gameId, ship, """{"crewClass": "A"}""").expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("$ship's crew is already class A.")
		replaceCrew(other, gameId, ship, """{"crewClass": "Z"}""").expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose a crew class: A, B, C or D.")
	}

	@Test
	fun shouldRefitInAnyPortAndRefuseShipyardActionsOutsideInvestment() {
		// R65 (creator, 2026-11-17): the other's first ship is in port at Gamma Leporis, which has no shipyard, and refits there.
		val (_, other, gameId) = investingAtTau("ship-refit-bad")
		val atGamma = gameStore.find(gameId)!!.players.first { it.email == other.removePrefix("token-") }.ships.first().name

		removeModule(other, gameId, atGamma, "LIGHT_WEAPONS").expectStatus().isOk
		addModule(other, gameId, atGamma, """{"module": "WARP"}""").expectStatus().isBadRequest
		removeModule(other, gameId, atGamma, "WARP").expectStatus().isBadRequest

		val (token, _, running) = startedGame("ship-bidding")
		val ship = gameStore.find(running)!!.players.first().ships.first().name
		sellShip(
			token,
			running,
			ship
		).expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody(String::class.java).isEqualTo("Investment isn't open.")
		sellShip(null, running, ship).expectStatus().isUnauthorized
	}

	@Test
	fun shouldBuildAFactoryAtTheDeckPriceAndShowTheLogLineButNotThePrice() {
		// E06-S05-1, S05-2, S05-3, S05-7, R50
		val (_, other, gameId) = investingAtTau("units")
		val before = pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val gamma = before.you!!.invest!!.factories.single { it.goods == "POLY" }
		assertThat(gamma.system).isEqualTo("GAMMA_LEPORIS")
		assertThat(gamma.size).isZero()
		assertThat(gamma.unitCost).isEqualTo(5 * gamma.price + 10)
		assertThat(before.board.single { it.id == "GAMMA_LEPORIS" }.market.single { it.goods == "POLY" }.price).isEqualTo(gamma.price)

		val bought = buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY", "units": 2}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(bought.you!!.money).isEqualTo(before.you!!.money - 2 * gamma.unitCost)
		assertThat(bought.you!!.invest!!.factories.single { it.goods == "POLY" }.size).isEqualTo(2)
		val me = bought.players.single { it.id == idOf(gameId, other.removePrefix("token-")) }
		assertThat(me.factories).containsExactly(FactoryResponseDto("GAMMA_LEPORIS", "POLY", 2, 0))
		assertThat(bought.log.last().text).isEqualTo("${me.corporationName} added 2 units of POLY production at Gamma Leporis")
		assertThat(bought.log.last().text).doesNotContain("HT")
	}

	@Test
	fun shouldRefuseFactoryUnitsBeyondPoliticalPlusBusinessAndShowTheRoomLeft() {
		// R84, E12-S03-7, S03-8: 6 units owned, political 5 + business 5
		val (owner, _, gameId) = investingAtTau("units-cap")
		val collected = pollGame(owner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(collected.you!!.invest!!.factoryUnits).isEqualTo(6)
		assertThat(collected.you!!.invest!!.factoryCap).isEqualTo(10)

		buyUnits(owner, gameId, """{"system": "TAU_CETI", "goods": "ISO", "units": 5}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("You can only own 10 units of factory (POL: 5, BUSS: 5).")
		buyUnits(owner, gameId, """{"system": "TAU_CETI", "goods": "ISO", "units": 4}""").expectStatus().isOk
	}

	@Test
	fun shouldRaiseOneConnectionPerInvestmentAndLogItWithoutTheCost() {
		// R83, E12-S02-4, S02-5: business 5 → 6 costs 60 HT
		val (owner, other, gameId) = investingAtTau("raise")
		val before = pollGame(owner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(
			before.you!!.invest!!.connections.single {
				it.kind == "BUSINESS"
			}
		).isEqualTo(ConnectionRaiseResponseDto("BUSINESS", "business", 5, 60))

		val raised = raiseConnection(owner, gameId, """{"kind": "BUSINESS"}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(raised.you!!.money).isEqualTo(before.you!!.money - 60)
		assertThat(raised.players.single { it.id == raised.you!!.id }.connections).isEqualTo(ConnectionsResponseDto(5, 6, 0))
		assertThat(raised.you!!.invest!!.raisedConnection).isTrue()
		assertThat(raised.log.dropLast(1).last().text).endsWith("raised business connections to 6").doesNotContain("HT")
		raiseConnection(owner, gameId, """{"kind": "POLITICAL"}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("You've already raised a connection this Investment.")
		raiseConnection(other, gameId, """{"kind": "SOCIAL"}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose political, business or criminal connections.")
		raiseConnection(null, gameId, """{"kind": "BUSINESS"}""").expectStatus().isUnauthorized
		raiseConnection(signIn("raise-stranger@example.com"), gameId, """{"kind": "BUSINESS"}""").expectStatus().isForbidden
	}

	@Test
	fun shouldExtendAnExistingFactoryAfterTheOwnerCollects() {
		// E06-S05-3, S03-5, R48: one factory per good per system, so the size grows; nothing is produced for it this turn
		val (owner, _, gameId) = investingAtTau("units-extend")

		val extended = buyUnits(owner, gameId, """{"system": "TAU_CETI", "goods": "ISO", "units": 2}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(extended.you!!.invest!!.factories.single { it.system == "TAU_CETI" && it.goods == "ISO" }.size).isEqualTo(8)
		assertThat(
			extended.players.single {
				it.id == idOf(gameId, owner.removePrefix("token-"))
			}.factories
		).containsExactly(FactoryResponseDto("TAU_CETI", "ISO", 8, 0))
		assertThat(extended.you!!.ships.flatMap { it.cargo.keys }).isEmpty()
	}

	@Test
	fun shouldLetAPlayerWithNoFactoryBuildTheirFirstAndStillSignalDone() {
		// R48: building a first factory this turn doesn't make them collect from it
		val (_, other, gameId) = investingAtTau("units-first")

		buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY", "units": 1}""").expectStatus().isOk
		val after = pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(after.you!!.collect).isNull()
		assertThat(after.you!!.invest).isNotNull()
		signalDoneInvesting(other, gameId).expectStatus().isOk
	}

	@Test
	fun shouldOfferEverySystemThatCanProduceAndNeedNoShipThere() {
		// E06-S05-1, S05-5, creator 2026-11-16: the owner has a ship only at Tau Ceti, the other only at Gamma Leporis
		val (owner, other, gameId) = investingAtTau("units-offer")

		val seenByOwner = pollGame(owner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val seenByOther = pollGame(other, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		for (seen in listOf(seenByOwner, seenByOther)) {
			assertThat(seen.you!!.invest!!.factories.map { it.system to it.goods }).containsExactly(
				"GAMMA_LEPORIS" to "POLY",
				"TAU_CETI" to "ISO",
				"MU_HERCULIS" to "CHIPS",
				"SIGMA_DRACONIS" to "FOOD",
				"SIGMA_DRACONIS" to "ISO"
			)
		}
	}

	@Test
	fun shouldBuildAFactoryWhereThePlayerHasNoShip() {
		// creator, 2026-11-16: Mu Herculis makes CHIPS; neither player has a ship or a factory there
		val (_, other, gameId) = investingAtTau("units-noship")
		val otherEmail = other.removePrefix("token-")

		val bought = buyUnits(other, gameId, """{"system": "MU_HERCULIS", "goods": "CHIPS", "units": 1}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(
			bought.players.single {
				it.id == idOf(gameId, otherEmail)
			}.factories
		).containsExactly(FactoryResponseDto("MU_HERCULIS", "CHIPS", 1, 0))
	}

	@Test
	fun shouldRefuseFactoryUnitsTheRulesRefuse() {
		// E06-S05-4, S05-5: a good the system can't make, a system that makes nothing, too little money, a bad amount, an unknown name
		val (_, other, gameId) = investingAtTau("units-refused")

		buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "ISO", "units": 1}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Gamma Leporis can't produce ISO.")
		buyUnits(other, gameId, """{"system": "BETA_HYDRI", "goods": "ISO", "units": 1}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Beta Hydri can't produce ISO.")
		buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY", "units": 1000}""").expectStatus().isBadRequest
		buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY", "units": 0}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Enter a whole number of units, 1 or more.")
		buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY", "units": 1.5}""").expectStatus().isBadRequest
		buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY"}""").expectStatus().isBadRequest
		buyUnits(other, gameId, """{"system": "GAMMA_LEPORIS", "goods": "GOLD", "units": 1}""").expectStatus().isBadRequest
		buyUnits(other, gameId, """{"system": "NOWHERE", "goods": "POLY", "units": 1}""").expectStatus().isBadRequest
		buyUnits(other, gameId, "{}").expectStatus().isBadRequest
		val otherEmail = other.removePrefix("token-")
		assertThat(gameStore.find(gameId)!!.players.first { it.email == otherEmail }.factories).isEmpty()
	}

	@Test
	fun shouldRefuseFactoryUnitsFromOutsidersOrOutsideInvestment() {
		val (first, _, gameId) = orderedGame("units-outside")

		buyUnits("token-$first", gameId, """{"system": "TAU_CETI", "goods": "ISO", "units": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Investment isn't open.")
		buyUnits(null, gameId, "{}").expectStatus().isUnauthorized
		buyUnits(
			signIn("units-stranger@example.com"),
			gameId,
			"""{"system": "TAU_CETI", "goods": "ISO", "units": 1}"""
		).expectStatus().isForbidden
		buyUnits("token-$first", "no-such-game", """{"system": "TAU_CETI", "goods": "ISO", "units": 1}""").expectStatus().isNotFound
	}

	@Test
	fun shouldRefuseACollectFromOutsidersOrOutsideOpportunity() {
		val (first, _, gameId) = orderedGame("collect-outside")

		collectOutput("token-$first", gameId, "{}")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Opportunity isn't open.")
		collectOutput(null, gameId, "{}").expectStatus().isUnauthorized
		collectOutput(signIn("collect-stranger@example.com"), gameId, "{}").expectStatus().isForbidden
		collectOutput("token-$first", "no-such-game", "{}").expectStatus().isNotFound
	}

	@Test
	fun shouldEndAOneTurnGameAfterItsInvestmentAndShowEveryonesFinalMoney() {
		// E05-S08, E06-S01-2: a one-turn game; with no offers and no positions, each good resolves on Resolve next, then Investment.
		val creatorToken = signIn("game-over-host@example.com")
		val joinerToken = signIn("game-over-guest@example.com", "Grace", "Grace Freight")
		val gameId = createGame(creatorToken, """{"turns": 1}""").expectBody(GameResponseDto::class.java).returnResult().responseBody!!.id
		joinGame(joinerToken, gameId).expectStatus().isOk
		startGame(creatorToken, gameId).expectStatus().isOk
		placeConnections(creatorToken, gameId)
		placeConnections(joinerToken, gameId)
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		finishJumping("token-$first", gameId).expectStatus().isOk
		finishJumping("token-$second", gameId).expectStatus().isOk
		repeat(18) { resolveNext("token-$first", gameId).expectStatus().isOk }
		assertThat(
			pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.status
		).isEqualTo("RUNNING")
		passOpportunity(gameId)
		signalDoneInvesting("token-$first", gameId).expectStatus().isOk
		signalDoneInvesting("token-$second", gameId).expectStatus().isOk

		// S08-1 to S08-4: over, and both players see both final amounts, tied at 300 HT, so both win.
		val over = pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(over.status).isEqualTo("FINISHED")
		assertThat(over.turn!!.number).isEqualTo(1)
		assertThat(over.result!!.standings.map { it.money }).containsExactly(300, 300)
		assertThat(over.result!!.winners).containsExactlyInAnyOrder("p1", "p2")
		assertThat(pollGame(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.result)
			.isEqualTo(over.result)
		// S08-5
		assertThat(over.log.last().text).isEqualTo("Game over: Adrian Star Traders and Grace Freight win with 300 HT")
		// S08-6: every action is refused.
		signalDone(
			joinerToken,
			gameId
		).expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody(String::class.java).isEqualTo("This game is over")
		resolveNext("token-$first", gameId).expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game is over")
	}

	@Test
	fun shouldRefuseTradingActionsOutsideTradingWithABadOrderOrFromOutsiders() {
		val (first, _, gameId) = orderedGame("trade-refused")
		val body = """{"queue": [{"system": "MU_HERCULIS", "goods": "ISO"}]}"""

		resolveNext("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Trading hasn't started.")
		reorderQueue("token-$first", gameId, body).expectStatus().isEqualTo(HttpStatus.CONFLICT)
		listOf("""{}""", """{"queue": [{"system": "NOWHERE", "goods": "ISO"}]}""", """{"queue": [{"system": "MU_HERCULIS"}]}""").forEach {
			reorderQueue("token-$first", gameId, it)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo("Send the remaining goods in their new order.")
		}
		resolveNext(signIn("trade-refused-stranger@example.com"), gameId).expectStatus().isForbidden
		resolveNext(signIn("trade-refused-lost@example.com"), "missing-game").expectStatus().isNotFound
		resolveNext(null, gameId).expectStatus().isUnauthorized
		reorderQueue(signIn("trade-refused-stranger2@example.com"), gameId, body).expectStatus().isForbidden
		reorderQueue(signIn("trade-refused-lost2@example.com"), "missing-game", body).expectStatus().isNotFound
		reorderQueue(null, gameId, body).expectStatus().isUnauthorized
	}

	@Test
	fun shouldDiscloseTheOffersAndLetTheDictatorPlaceOneBeforeTheNextGood() {
		// E05-S02: the guest is Dictator of POLY at Gamma Leporis, the first good to resolve; the host offered on it.
		val (creatorToken, joinerToken, gameId) = startedGame("dictator-trade")
		val host = "dictator-trade-host@example.com"
		val guest = "dictator-trade-guest@example.com"
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != guest) {
						player
					} else {
						player.copy(marketPositions = listOf(MarketPosition(StarSystem.GAMMA_LEPORIS, Goods.POLY, MarketTier.DICTATOR)))
					}
				}
			)
		}
		placeOffer(creatorToken, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY", "type": "BUY", "price": 11}""").expectStatus().isCreated
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		finishJumping("token-$first", gameId).expectStatus().isOk
		finishJumping("token-$second", gameId).expectStatus().isOk

		// S02-1, S02-2: the host's offer is public now, and the guest is asked.
		// The host created the game and the guest joined it, so the responses call them p1 and p2 (no email is sent).
		val asking = resolveNext("token-$first", gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val good = MarketGoodResponseDto("GAMMA_LEPORIS", "Gamma Leporis", "POLY")
		assertThat(asking.turn!!.resolution).isEqualTo(
			// R38: no roll while the Dictator decides
			ResolutionResponseDto(
				good,
				listOf(DisclosedOfferResponseDto("p1", "BUY", 11)),
				"p2",
				"p2",
				false,
				null,
				emptyList(),
				null,
				2,
				null,
				listOf("p2"),
				null,
				emptyList()
			)
		)
		assertThat(asking.turn!!.resolvedGoods).isEmpty()
		assertThat(pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.turn!!.resolution)
			.isEqualTo(asking.turn!!.resolution)

		// S01-4: the next good waits; R38: only the Dictator decides, with a valid offer.
		resolveNext("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Waiting for Grace Freight (Dictator) to decide.")
		passAsDictator(creatorToken, gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Waiting for Grace Freight (Dictator) to decide.")
		listOf("""{"type": "SELL", "price": 21}""", """{"type": "SELL", "price": 2.5}""", """{"type": "SELL"}""").forEach { body ->
			dictatorOffer(joinerToken, gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo("Price must be a whole number from 1 to 20.")
		}
		dictatorOffer(joinerToken, gameId, """{"type": "SWAP", "price": 9}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose Buy or Sell.")

		// S02-3: the Dictator's offer is disclosed like the others, and the good has resolved.
		val offered = dictatorOffer(joinerToken, gameId, """{"type": "SELL", "price": 9}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		// E05-S03: then the counter starts, at S/D +1 plus 2d6. Both offers saturate (R15, targets -18), so the host's buy
		// may take everything the counter allows, and E05-S04: the game waits for the host to choose.
		val resolution = offered.turn!!.resolution!!
		val counter = resolution.startingCounter!!
		assertThat(counter).isBetween(3, 13)
		val offers = listOf(DisclosedOfferResponseDto("p1", "BUY", 11), DisclosedOfferResponseDto("p2", "SELL", 9))
		assertThat(resolution).isEqualTo(
			ResolutionResponseDto(
				good,
				offers,
				"p2",
				null,
				false,
				counter,
				emptyList(),
				AwaitingTradeResponseDto("p1", "BUY", 11, counter + 18),
				2,
				null,
				listOf("p2"),
				null,
				emptyList()
			)
		)
		assertThat(offered.turn!!.resolvedGoods).isEmpty()
		assertThat(offered.log.map { it.text }).contains("Grace Freight (Dictator) offers to sell at 9 HT")
		passAsDictator(joinerToken, gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("No Dictator decision is pending.")

		// S04-1, S04-2, R29: only the host sees their own max, capped by 300 HT at 11 HT each (27 u).
		assertThat(offered.you!!.tradeMax).isNull()
		val hostView = pollGame(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val max = minOf(counter + 18, 27)
		assertThat(hostView.you!!.tradeMax).isEqualTo(max)
		val waiting = "Adrian Star Traders can buy up to ${counter + 18} u — waiting for their decision."
		resolveNext("token-$first", gameId).expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody(String::class.java).isEqualTo(waiting)
		trade(
			joinerToken,
			gameId,
			"""{"amount": 1}"""
		).expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody(String::class.java).isEqualTo(waiting)
		listOf("""{"amount": 0}""", """{"amount": 2.5}""", """{"amount": ${max + 1}}""", "{}").forEach { body ->
			trade(creatorToken, gameId, body)
				.expectStatus().isBadRequest
				.expectBody(String::class.java).isEqualTo("Enter a whole number from 1 to $max.")
		}
		trade(creatorToken, gameId, """{"amount": 1, "ships": [{"name": "Wayfarer", "units": 0.5}]}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Enter a whole number of units, 0 or more, for each ship.")

		// S04-5, R34: the host can buy, so trading nothing is refused.
		tradeNothing(creatorToken, gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("You can buy up to $max u: choose from 1 to $max.")
		tradeNothing(joinerToken, gameId).expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody(String::class.java).isEqualTo(waiting)

		// S04-3, S04-6, S04-7: the host buys 1 u into a ship of theirs there, if any (else it's lost, S04-4).
		val shipHere = hostView.players.first { it.id == idOf(gameId, host) }.ships.firstOrNull { it.system == "GAMMA_LEPORIS" }
		val ships = shipHere?.let { """[{"name": "${it.name}", "units": 1}]""" } ?: "[]"
		val traded = trade(creatorToken, gameId, """{"amount": 1, "ships": $ships}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(traded.you!!.money).isEqualTo(289)
		shipHere?.let { ship -> assertThat(traded.you!!.ships.first { it.name == ship.name }.cargo).isEqualTo(mapOf("POLY" to 1)) }
		// The guest's sell (target -18) still can't trade, so trading is over: the price moves (E05-S05-1) and the guest,
		// as Dictator, is asked to trade wholesale (R37).
		assertThat(traded.turn!!.resolution!!.trades).containsExactly(TradeResponseDto("p1", "BUY", 11, counter + 18, 1, null))
		val newPrice = traded.turn!!.resolution!!.newPrice!!
		assertThat(traded.turn!!.resolution!!.awaitingWholesaler).isEqualTo(idOf(gameId, guest))
		assertThat(traded.turn!!.resolution!!.finished).isFalse()
		assertThat(traded.turn!!.resolvedGoods).isEmpty()
		assertThat(traded.board.single { it.id == "GAMMA_LEPORIS" }.market.single().price).isEqualTo(newPrice)
		assertThat(
			traded.log.map {
				it.text
			}
		).contains(
			"Adrian Star Traders could buy up to ${counter + 18} u: bought 1 u at 11 HT each, paid 11 HT",
			"POLY price at Gamma Leporis: 2 → $newPrice HT"
		)
		trade(creatorToken, gameId, """{"amount": 1, "ships": $ships}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("No trade is waiting for a decision.")

		// E05-S05-4, R29: only the guest gets their own wholesale max; only they may answer; the host waits.
		val wholesaleWait = "Waiting for Grace Freight (Dictator) to trade up to 5 u at $newPrice HT, or pass."
		assertThat(traded.you!!.wholesaleMax).isNull()
		val guestView = pollGame(joinerToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(guestView.you!!.wholesaleMax).isEqualTo(WholesaleMaxResponseDto(minOf(5, 300 / newPrice), 0))
		resolveNext("token-$first", gameId).expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody(String::class.java).isEqualTo(wholesaleWait)
		passWholesale(creatorToken, gameId).expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody(String::class.java).isEqualTo(wholesaleWait)
		listOf("""{"type": "SWAP", "amount": 1}""", """{"amount": 1}""").forEach { body ->
			wholesale(joinerToken, gameId, body).expectStatus().isBadRequest.expectBody(String::class.java).isEqualTo("Choose Buy or Sell, or pass.")
		}
		wholesale(joinerToken, gameId, """{"type": "BUY", "amount": 6}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Enter a whole number from 1 to ${minOf(5, 300 / newPrice)}.")
		wholesale(joinerToken, gameId, """{"type": "SELL", "amount": 1}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("You have no POLY in your ships in port at Gamma Leporis, so you can't sell any.")

		// S05-5: the guest passes; it's public and logged, and the good has resolved.
		val passed = passWholesale(joinerToken, gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(passed.turn!!.resolution!!.wholesales).containsExactly(WholesaleResponseDto("p2", "Dictator", null, 0, newPrice))
		assertThat(passed.turn!!.resolution!!.finished).isTrue()
		assertThat(passed.turn!!.resolvedGoods).containsExactly(good)
		assertThat(passed.log.last().text).isEqualTo("Grace Freight (Dictator) passed on wholesale")
		passWholesale(joinerToken, gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("No wholesale decision is pending.")
		resolveNext("token-$first", gameId).expectStatus().isOk
	}

	@Test
	fun shouldNeverSendAnotherPlayersWarehouseCargo() {
		// E06-S06, S02-7, A05: a warehouse is public, what it holds is only its owner's
		val (creatorToken, joinerToken, gameId) = startedGame("warehouse-cargo")
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != "warehouse-cargo-guest@example.com") {
						player
					} else {
						player.copy(warehouses = listOf(Warehouse(StarSystem.TAU_CETI, 2, mapOf(Goods.ISO to 17, Goods.CHIPS to 23))))
					}
				}
			)
		}

		val seenByCreator = pollGame(creatorToken, gameId).expectStatus().isOk
			.expectBody(String::class.java).returnResult().responseBody!!
		val seenByJoiner = pollGame(joinerToken, gameId).expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(seenByCreator).doesNotContain("\"ISO\":17").doesNotContain("\"CHIPS\":23")
		assertThat(seenByJoiner.you!!.warehouses)
			.containsExactly(YourWarehouseResponseDto("TAU_CETI", "Tau Ceti", 2, 20, mapOf("CHIPS" to 23, "ISO" to 17), 17, emptyList()))
	}

	@Test
	fun shouldLetATraderWithOnlyAWarehouseThereBuyIntoItOverHttp() {
		// E06-S06, R49: the host's ships are away and their warehouse at Gamma Leporis, the first good to resolve, takes the buy
		val (creatorToken, joinerToken, gameId) = startedGame("warehouse-trade")
		val host = "warehouse-trade-host@example.com"
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != host) {
						player
					} else {
						player.copy(
							ships = player.ships.map { it.copy(location = ShipLocation.SPACE) },
							warehouses = listOf(Warehouse(StarSystem.GAMMA_LEPORIS, 1, emptyMap()))
						)
					}
				}
			)
		}
		placeOffer(creatorToken, gameId, """{"system": "GAMMA_LEPORIS", "goods": "POLY", "type": "BUY", "price": 11}""").expectStatus().isCreated
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		finishJumping("token-$first", gameId).expectStatus().isOk
		finishJumping("token-$second", gameId).expectStatus().isOk
		resolveNext("token-$first", gameId).expectStatus().isOk

		// the split must add up, and the warehouse can't be overfilled past what's paid for
		trade(creatorToken, gameId, """{"amount": 5, "warehouse": 4}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Your ships' and warehouse's units must add up to 5.")
		trade(joinerToken, gameId, """{"amount": 5, "warehouse": 5}""").expectStatus().isEqualTo(HttpStatus.CONFLICT)
		val traded = trade(creatorToken, gameId, """{"amount": 5, "warehouse": 5}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(traded.you!!.money).isEqualTo(245)
		assertThat(
			traded.you!!.warehouses
		).containsExactly(YourWarehouseResponseDto("GAMMA_LEPORIS", "Gamma Leporis", 1, 10, mapOf("POLY" to 5), 5, emptyList()))
		assertThat(
			traded.log.map {
				it.text
			}
		).contains(
			"Adrian Star Traders could buy up to ${traded.turn!!.resolution!!.trades.single().marketMax} u: bought 5 u at 11 HT each, paid 55 HT"
		)
	}

	@Test
	fun shouldRefuseWarehouseUnitsThatAreNotAWholeNumber() {
		// E06-S06, R49: the request's warehouse field is checked like a ship's units
		val (first, _, gameId) = orderedGame("trade-warehouse-units")

		trade("token-$first", gameId, """{"amount": 1, "warehouse": 0.5}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Enter a whole number of units, 0 or more, for your warehouse.")
		wholesale("token-$first", gameId, """{"type": "BUY", "amount": 1, "warehouse": 1.5}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Enter a whole number of units, 0 or more, for your warehouse.")
	}

	@Test
	fun shouldRefuseATradeOutsideTradingOrFromOutsiders() {
		val (first, _, gameId) = orderedGame("trade-refused")

		trade("token-$first", gameId, """{"amount": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Trading hasn't started.")
		trade(signIn("trade-refused-stranger@example.com"), gameId, """{"amount": 1}""").expectStatus().isForbidden
		trade(signIn("trade-refused-lost@example.com"), "missing-game", """{"amount": 1}""").expectStatus().isNotFound
		trade(null, gameId, """{"amount": 1}""").expectStatus().isUnauthorized
		tradeNothing("token-$first", gameId).expectStatus().isEqualTo(HttpStatus.CONFLICT)
		tradeNothing(signIn("trade-refused-stranger2@example.com"), gameId).expectStatus().isForbidden
		tradeNothing(null, gameId).expectStatus().isUnauthorized
		// E05-S05: the wholesale endpoints are refused the same way
		wholesale("token-$first", gameId, """{"type": "BUY", "amount": 1}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Trading hasn't started.")
		wholesale(signIn("trade-refused-stranger3@example.com"), gameId, """{"type": "BUY", "amount": 1}""").expectStatus().isForbidden
		wholesale(signIn("trade-refused-lost2@example.com"), "missing-game", """{"type": "BUY", "amount": 1}""").expectStatus().isNotFound
		wholesale(null, gameId, """{"type": "BUY", "amount": 1}""").expectStatus().isUnauthorized
		passWholesale("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Trading hasn't started.")
		passWholesale(signIn("trade-refused-stranger4@example.com"), gameId).expectStatus().isForbidden
		passWholesale(signIn("trade-refused-lost3@example.com"), "missing-game").expectStatus().isNotFound
		passWholesale(null, gameId).expectStatus().isUnauthorized
	}

	private fun wholesale(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/trading/wholesale", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun passWholesale(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/trading/wholesale-pass", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun tradeNothing(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/trading/trade-nothing", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun trade(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/trading/trade", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	@Test
	fun shouldRefuseADictatorDecisionWhenNobodyIsAskedOrFromOutsiders() {
		val (first, _, gameId) = orderedGame("dictator-refused")

		passAsDictator("token-$first", gameId)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("Trading hasn't started.")
		dictatorOffer("token-$first", gameId, """{"type": "BUY", "price": 5}""").expectStatus().isEqualTo(HttpStatus.CONFLICT)
		passAsDictator(signIn("dictator-refused-stranger@example.com"), gameId).expectStatus().isForbidden
		passAsDictator(signIn("dictator-refused-lost@example.com"), "missing-game").expectStatus().isNotFound
		passAsDictator(null, gameId).expectStatus().isUnauthorized
		dictatorOffer(signIn("dictator-refused-stranger2@example.com"), gameId, """{"type": "BUY", "price": 5}""").expectStatus().isForbidden
		dictatorOffer(null, gameId, """{"type": "BUY", "price": 5}""").expectStatus().isUnauthorized
	}

	private fun dictatorOffer(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/trading/dictator-offer", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun passAsDictator(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/trading/dictator-pass", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun resolveNext(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/trading/resolve-next", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun reorderQueue(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.put().uri("/api/games/{id}/trading/queue", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	@Test
	fun shouldKeepBattleOrdersSecretUntilEveryoneHasGivenThemAndShowTheShipsToEveryone() {
		// E10-S03-2, S03-3, S05-3
		val (first, second, gameId) = battleGame("battle-orders")
		val (ours, theirs) = firstShipsOf(gameId, first, second)

		val afterOrders = battleOrders(
			"token-$first",
			gameId,
			"""{"orders": [{"ship": "$ours", "action": "FIRE", "target": "$theirs", "rockets": true}]}"""
		)
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(afterOrders.you!!.battle!!.orders).containsExactly(BattleOrderResponseDto(ours, "FIRE", theirs, true))
		val seen = pollGame("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(seen.you!!.battle!!.orders).isEmpty()
		assertThat(seen.you!!.battle!!.fighting).containsExactly(theirs)
		assertThat(seen.turn!!.battle!!.waitingFor).containsExactly(idOf(gameId, second))
		assertThat(seen.turn!!.battle!!.ships.map { it.hull.name }).containsExactly("Clarinet", "Clarinet")
		assertThat(seen.turn!!.battle!!.ships.first { it.name == theirs }.interceptedBy).containsExactly(ours)
	}

	@Test
	fun shouldRefuseABattleOrderTheRulesDoNotAllow() {
		// E10-S03-2
		val (first, second, gameId) = battleGame("battle-refusals")
		val (ours, theirs) = firstShipsOf(gameId, first, second)

		battleOrders("token-$second", gameId, """{"orders": [{"ship": "$theirs", "action": "BREAK_AWAY"}]}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("$theirs was intercepted by $ours: it can't break away while $ours fights.")
		battleOrders("token-$first", gameId, """{"orders": [{"ship": "$ours", "action": "DANCE"}]}""")
			.expectStatus().isBadRequest
		battleOrders("token-$first", gameId, """{"orders": [{"ship": "$ours", "action": "BREAK_AWAY"}]}""").expectStatus().isOk
		battleOrders("token-$first", gameId, """{"orders": [{"ship": "$ours", "action": "BREAK_AWAY"}]}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
		jumpShip("token-$first", gameId, """{"ship": "$ours", "destination": "BETA_HYDRI"}""").expectStatus().isEqualTo(HttpStatus.CONFLICT)
		battleLanding("token-$first", gameId, """{"ships": [{"ship": "$ours", "location": "PORT"}]}""")
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
	}

	@Test
	fun shouldEndTheBattleWhenTheOnlyEnemyBreaksAwayAndLetEveryoneLand() {
		// E10-S04: the intercepted ship's salvo misses the ship breaking away; both then land.
		val (first, second, gameId) = battleGame("battle-landing")
		val (ours, theirs) = firstShipsOf(gameId, first, second)

		battleOrders("token-$first", gameId, """{"orders": [{"ship": "$ours", "action": "BREAK_AWAY"}]}""").expectStatus().isOk
		val over = battleOrders("token-$second", gameId, """{"orders": [{"ship": "$theirs", "action": "FIRE", "target": "$ours"}]}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(over.turn!!.battle!!.stage).isEqualTo("LANDING")
		assertThat(over.you!!.battle!!.toLand).containsExactly(theirs)

		battleLanding("token-$first", gameId, """{"ships": [{"ship": "$ours", "location": "SHIPYARD"}]}""").expectStatus().isBadRequest
		battleLanding("token-$first", gameId, """{"ships": [{"ship": "$ours", "location": "PORT"}]}""").expectStatus().isOk
		val landed = battleLanding("token-$second", gameId, """{"ships": [{"ship": "$theirs", "location": "SPACE"}]}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(landed.turn!!.battle).isNull()
		assertThat(landed.players.flatMap { it.ships }.first { it.name == theirs }.location).isEqualTo("SPACE")
	}

	@Test
	fun shouldTakeAnInterceptionDecisionOnlyFromAPlayerWhoIsAsked() {
		// E10-S02-2, S02-5. The jump is a move within the ship's own system (R69), which never fails, so real dice can't spoil it.
		val (first, second, gameId) = orderedGame("interception")
		val (ours, theirs) = firstShipsOf(gameId, first, second)
		val home = gameStore.find(gameId)!!.players.flatMap { it.ships }.first { it.name == ours }.system
		gameStore.update(gameId) { game ->
			val waiting = game.players.map { player ->
				player.copy(ships = player.ships.map { if (it.name == theirs) it.copy(system = home, location = ShipLocation.SPACE) else it })
			}
			game.copy(players = waiting)
		}
		val arrived = jumpShip("token-$first", gameId, """{"ship": "$ours", "destination": "${home.name}"}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(arrived.turn!!.interception!!.awaiting).containsExactly(idOf(gameId, second))

		intercept("token-$first", gameId, """{}""").expectStatus().isEqualTo(HttpStatus.CONFLICT)
		val asked = pollGame("token-$second", gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(asked.you!!.interception).isEqualTo(YourInterceptionResponseDto(listOf(theirs), answered = false, chosen = null))
		val decided = intercept("token-$second", gameId, """{}""")
			.expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(decided.turn!!.interception).isNull()
		assertThat(decided.log.last().text).isEqualTo("Nobody tries to intercept $ours")
	}

	@Test
	fun shouldRefuseARepairOutsideInvestment() {
		// E10-S05-2
		val (first, _, gameId) = orderedGame("repair")
		val ship = gameStore.find(gameId)!!.players.first { it.email == first }.ships.first().name

		repairShip("token-$first", gameId, ship).expectStatus().isEqualTo(HttpStatus.CONFLICT)
	}

	/** An [orderedGame] whose two players' first ships fight in Space at Tau Ceti, the second's intercepted by the first's. */
	private fun battleGame(prefix: String): Triple<String, String, String> {
		val (first, second, gameId) = orderedGame(prefix)
		val (ours, theirs) = firstShipsOf(gameId, first, second)
		gameStore.update(gameId) { game ->
			val inSpace = game.players.map { player ->
				player.copy(
					ships = player.ships.map {
						if (it.name == ours ||
							it.name == theirs
						) {
							it.copy(system = StarSystem.TAU_CETI, location = ShipLocation.SPACE)
						} else {
							it
						}
					}
				)
			}
			val battle = Battle.start(
				StarSystem.TAU_CETI,
				listOf(BattleShip(ours, first), BattleShip(theirs, second)),
				listOf(BattleInterception(theirs, ours)),
				listOf(BattleInterception(theirs, ours))
			)
			game.copy(players = inSpace, turn = game.turn!!.withBattle(battle))
		}
		return Triple(first, second, gameId)
	}

	private fun firstShipsOf(gameId: String, first: String, second: String): Pair<String, String> {
		val game = gameStore.find(gameId)!!
		return game.players.first { it.email == first }.ships.first().name to game.players.first { it.email == second }.ships.first().name
	}

	private fun intercept(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/interception", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun battleOrders(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/battle/orders", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun battleLanding(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/battle/landing", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun repairShip(token: String?, gameId: String, ship: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/ships/{ship}/repair", gameId, ship)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	/**
	 * A two-player game for this test's own emails with Bidding closed and the turn order set, the first in
	 * initiative order taking 1st: the email in 1st place, the one in 2nd, the game id. Tokens are "token-{email}".
	 */
	private fun orderedGame(prefix: String): Triple<String, String, String> {
		val (creatorToken, joinerToken, gameId) = startedGame(prefix)
		signalDone(joinerToken, gameId).expectStatus().isOk
		val closed = signalDone(creatorToken, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val (first, second) = gameStore.find(gameId)!!.turn!!.initiative
		pickPlace("token-$first", gameId, """{"place": 1}""").expectStatus().isOk
		return Triple(first, second, gameId)
	}

	private fun jumpShip(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/jumps", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	/**
	 * A system the viewer's ship [shipName] is certain to reach (R68): every starting ship has one, as its crew and Safe
	 * jump add 5 and every system has a route of base 7 or more. The jump roll is random here, so tests jump there.
	 */
	private fun certainDestination(token: String, gameId: String, shipName: String): StarSystem {
		val you = pollGame(token, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!.you!!
		val chances = you.ships.first { it.name == shipName }.jumpChances
		return StarSystem.valueOf(chances.entries.first { it.value.percent == 100 }.key)
	}

	private fun landShip(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/landing", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	/**
	 * A started two-player game in turn 1's Bidding where the creator's first ship is in port at Tau Ceti carrying 3 ISO, and
	 * they have a one-block warehouse there holding 6 ISO (the test places both, since the start is random and gives none).
	 * Returns the creator's token, the joiner's token and the game id.
	 */
	private fun gameWithAWarehouse(prefix: String): Triple<String, String, String> {
		val (creator, joiner, gameId) = startedGame(prefix)
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email != game.creatorEmail) {
						player
					} else {
						val docked = player.ships[0].copy(system = StarSystem.TAU_CETI, location = ShipLocation.PORT, cargo = mapOf(Goods.ISO to 3))
						player.copy(
							ships = listOf(docked) + player.ships.drop(1),
							warehouses = listOf(Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.ISO to 6)))
						)
					}
				}
			)
		}
		return Triple(creator, joiner, gameId)
	}

	@Test
	fun shouldMoveGoodsFromTheWarehouseOntoAShipInPortDuringBidding() {
		// E08-S02-1, R58: in any phase, here Bidding, where nothing else touches cargo
		val (creator, _, gameId) = gameWithAWarehouse("cargo-load")
		val ship = gameStore.find(gameId)!!.players.first().ships[0].name

		val moved = moveCargo(creator, gameId, """{"to": "$ship", "goods": "ISO", "units": 4}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(moved.turn!!.phase).isEqualTo("BIDDING")
		assertThat(moved.you!!.ships.single { it.name == ship }.cargo).isEqualTo(mapOf("ISO" to 7))
		assertThat(moved.you!!.ships.single { it.name == ship }.cargoUsed).isEqualTo(7)
		assertThat(moved.you!!.warehouses.single().cargo).isEqualTo(mapOf("ISO" to 2))
		assertThat(GameResponseDto(gameStore.find(gameId)!!, gameStore.find(gameId)!!.creatorEmail)).isEqualTo(moved)
	}

	@Test
	fun shouldMoveGoodsFromAShipIntoTheWarehouse() {
		val (creator, _, gameId) = gameWithAWarehouse("cargo-unload")
		val ship = gameStore.find(gameId)!!.players.first().ships[0].name

		val moved = moveCargo(creator, gameId, """{"from": "$ship", "goods": "ISO", "units": 3}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(moved.you!!.ships.single { it.name == ship }.cargo).isEmpty()
		assertThat(moved.you!!.warehouses.single().cargo).isEqualTo(mapOf("ISO" to 9))
	}

	@Test
	fun shouldMoveGoodsBetweenTwoShipsInTheSamePort() {
		// R58, revised 2026-11-17: ship to ship, with no warehouse involved
		val (creator, _, gameId) = gameWithAWarehouse("cargo-ship-to-ship")
		val ship = gameStore.find(gameId)!!.players.first().ships[0].name
		gameStore.update(gameId) { game ->
			game.copy(
				players = game.players.map { player ->
					if (player.email !=
						game.creatorEmail
					) {
						player
					} else {
						player.copy(ships = player.ships + Ship.emptyAt("Comet", StarSystem.TAU_CETI, ShipLocation.PORT))
					}
				}
			)
		}

		val moved = moveCargo(creator, gameId, """{"from": "$ship", "to": "Comet", "goods": "ISO", "units": 2}""").expectStatus().isOk
			.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(moved.you!!.ships.single { it.name == ship }.cargo).isEqualTo(mapOf("ISO" to 1))
		assertThat(moved.you!!.ships.single { it.name == "Comet" }.cargo).isEqualTo(mapOf("ISO" to 2))
		assertThat(moved.you!!.warehouses.single().cargo).isEqualTo(mapOf("ISO" to 6))
		moveCargo(creator, gameId, """{"from": "$ship", "to": "$ship", "goods": "ISO", "units": 1}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose two different ships.")
	}

	@Test
	fun shouldKeepACargoMovePrivate() {
		// E08-S02-6, R29: no log line, and the other player's view is what it was
		val (creator, joiner, gameId) = gameWithAWarehouse("cargo-private")
		val ship = gameStore.find(gameId)!!.players.first().ships[0].name
		val before = pollGame(joiner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		moveCargo(creator, gameId, """{"to": "$ship", "goods": "ISO", "units": 4}""").expectStatus().isOk

		val after = pollGame(joiner, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(after).isEqualTo(before)
	}

	@Test
	fun shouldRefuseACargoMoveTheRulesRefuseAndLoseNothing() {
		// E08-S02-2: more than the source holds, more than fits, a ship that isn't the caller's
		val (creator, joiner, gameId) = gameWithAWarehouse("cargo-refused")
		val stored = gameStore.find(gameId)!!
		val ship = stored.players.first().ships[0].name

		moveCargo(creator, gameId, """{"to": "$ship", "goods": "ISO", "units": 7}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Your warehouse at Tau Ceti has only 6 ISO.")
		moveCargo(creator, gameId, """{"to": "$ship", "goods": "ISO", "units": 6}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("$ship has room for only 5 more ISO.")
		moveCargo(joiner, gameId, """{"from": "$ship", "goods": "ISO", "units": 1}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Choose one of your ships.")

		assertThat(gameStore.find(gameId)).isEqualTo(stored)
	}

	@Test
	fun shouldRefuseACargoMoveThatIsNotWellFormed() {
		val (creator, _, gameId) = gameWithAWarehouse("cargo-malformed")
		val ship = gameStore.find(gameId)!!.players.first().ships[0].name
		val message = "Choose where the goods come from and go, a good and a whole number of units, 1 or more."

		for (
		body in listOf(
			"""{"to": "$ship", "goods": "ISO", "units": 2.5}""",
			"""{"to": "$ship", "goods": "GOLD", "units": 1}""",
			"""{"to": "$ship", "goods": "ISO"}""",
			"{}"
		)
		) {
			moveCargo(creator, gameId, body).expectStatus().isBadRequest.expectBody(String::class.java).isEqualTo(message)
		}
		moveCargo(creator, gameId, """{"to": "$ship", "goods": "ISO", "units": 0}""")
			.expectStatus().isBadRequest
			.expectBody(String::class.java).isEqualTo("Enter a whole number of units, 1 or more.")
	}

	@Test
	fun shouldRefuseACargoMoveFromAnyoneButAPlayerOfARunningGame() {
		// E08-S02-4
		val (_, _, gameId) = gameWithAWarehouse("cargo-access")
		val body = """{"to": "Wayfarer", "goods": "ISO", "units": 1}"""
		val outsider = signIn("cargo-outsider@example.com")
		val lobby = createdGame(outsider)

		moveCargo(null, gameId, body).expectStatus().isUnauthorized
		moveCargo(outsider, gameId, body).expectStatus().isForbidden
		moveCargo(outsider, "no-such-game", body).expectStatus().isNotFound
		moveCargo(outsider, lobby.id, body)
			.expectStatus().isEqualTo(HttpStatus.CONFLICT)
			.expectBody(String::class.java).isEqualTo("This game hasn't started")
	}

	@Test
	fun shouldSendOnlyTheLogEntriesThePollDoesNotHaveYet() {
		// the 2-second poll mustn't carry the whole log every time
		val (creator, _, gameId) = startedGame("log-after")
		val whole = pollGame(creator, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		assertThat(whole.log).hasSize(6)
		assertThat(whole.logOffset).isZero()

		val newer = pollLogAfter(creator, gameId, "1").expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val none = pollLogAfter(creator, gameId, "6").expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

		assertThat(newer.log).isEqualTo(whole.log.drop(1))
		assertThat(newer.logOffset).isEqualTo(1)
		assertThat(none.log).isEmpty()
		assertThat(none.logOffset).isEqualTo(6)
		assertThat(newer.copy(log = whole.log, logOffset = 0)).isEqualTo(whole)
	}

	@Test
	fun shouldSendTheWholeLogToAPollThatIsOutOfStep() {
		val (creator, _, gameId) = startedGame("log-out-of-step")

		for (count in listOf("7", "-1")) {
			val game = pollLogAfter(creator, gameId, count).expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

			assertThat(game.log).hasSize(6)
			assertThat(game.logOffset).isZero()
		}
		pollLogAfter(creator, gameId, "many").expectStatus().isBadRequest
	}

	private fun pollLogAfter(token: String, gameId: String, count: String): RestTestClient.ResponseSpec =
		restTestClient.get().uri("/api/games/{id}?logAfter={count}", gameId, count).header("Authorization", "Bearer $token").exchange()

	private fun moveCargo(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/cargo/moves", gameId).contentType(MediaType.APPLICATION_JSON)
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
		placeConnections(creatorToken, game.id)
		placeConnections(joinerToken, game.id)
		return Triple(creatorToken, joinerToken, game.id)
	}

	/**
	 * The caller places their 4 to 6 starting points (R82) as political 3 and the rest business, so turn 1's Bidding can
	 * close; their factory cap is then their points (R84).
	 */
	private fun placeConnections(token: String, gameId: String) {
		val game = pollGame(token, gameId).expectBody(GameResponseDto::class.java).returnResult().responseBody!!
		val points = game.players.single { it.id == game.you!!.id }.connectionPoints
		placement(token, gameId, """{"political": 3, "business": ${points - 3}, "criminal": 0}""").expectStatus().isOk
	}

	private fun placement(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/connections/placement", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun raiseConnection(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/connections/raise", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun signalDone(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/bidding/done", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun collectOutput(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/opportunity/collect", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun buyBlocks(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/warehouse-blocks", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun buyUnits(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/factory-units", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun buyShip(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/ships", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun buyReputation(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/reputation/buy", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun replaceCrew(token: String?, gameId: String, ship: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.put().uri("/api/games/{id}/investment/ships/{ship}/crew", gameId, ship)
			.contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun addModule(token: String?, gameId: String, ship: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/ships/{ship}/modules", gameId, ship)
			.contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun storeModule(token: String?, gameId: String, ship: String, module: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/ships/{ship}/modules/{module}/store", gameId, ship, module)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun fitStoredModule(token: String?, gameId: String, ship: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/ships/{ship}/modules/stored", gameId, ship)
			.contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun moveModule(token: String?, gameId: String, ship: String, module: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/ships/{ship}/modules/{module}/move", gameId, ship, module)
			.contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun removeModule(token: String?, gameId: String, ship: String, module: String): RestTestClient.ResponseSpec {
		val request = restTestClient.delete().uri("/api/games/{id}/investment/ships/{ship}/modules/{module}", gameId, ship, module)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun sellShip(token: String?, gameId: String, ship: String): RestTestClient.ResponseSpec {
		val request = restTestClient.delete().uri("/api/games/{id}/investment/ships/{ship}", gameId, ship)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun finishOpportunities(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/opportunity/done", gameId)
		return (if (token == null) request else request.header("Authorization", "Bearer $token")).exchange()
	}

	/** Every player, in turn order, done with opportunities after collecting nothing if they have factories (R102). */
	private fun passOpportunity(gameId: String) {
		gameStore.update(gameId) { OpportunityPassed(it).byEveryone() }
	}

	private fun signalDoneInvesting(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/investment/done", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun placeOffer(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/offers", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.body(body).exchange()
	}

	private fun withdrawOffer(token: String?, gameId: String, system: String, goods: String): RestTestClient.ResponseSpec {
		val request = restTestClient.delete().uri("/api/games/{id}/offers/{system}/{goods}", gameId, system, goods)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	/**
	 * Picks a place; once the last pick opens News (R109), every player is marked done with it, so tests about the jumps
	 * that follow needn't read the news. The response is the pick's own.
	 */
	private fun pickPlace(token: String?, gameId: String, body: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/ship-jumps/turn-order", gameId).contentType(MediaType.APPLICATION_JSON)
		token?.let { request.header("Authorization", "Bearer $it") }
		val response = request.body(body).exchange()
		if (gameStore.find(gameId)?.turn?.phase == TurnPhase.NEWS) {
			gameStore.update(gameId) { game -> game.players.fold(game) { done, player -> done.signalDoneNews(player.email) } }
		}
		return response
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

	/** The id [email]'s player goes by in the responses of game [gameId] ([PlayerIds]): no response carries an email. */
	private fun idOf(gameId: String, email: String): String = PlayerIds(gameStore.find(gameId)!!).of(email)

	private fun signIn(email: String, name: String = "Adrian", corporationName: String = "Adrian Star Traders"): String {
		val token = "token-$email"
		sessionStore.store(Session(token, name, corporationName, email))
		return token
	}

	private fun createdGame(token: String): GameResponseDto =
		createGame(token, """{"turns": 20}""").expectBody(GameResponseDto::class.java).returnResult().responseBody!!

	/**
	 * Joins as players do since R120: the caller asks, and when the request is waiting, the creator (signed in by
	 * [signIn]'s token convention) accepts it, answering with the acceptance; otherwise with the refused request.
	 */
	private fun joinGame(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val asked = requestToJoin(token, gameId)
		val email = token?.removePrefix("token-")
		val game = gameStore.find(gameId)
		if (email == null || game == null || !game.hasAskedToJoin(email)) {
			return asked
		}
		asked.expectStatus().isOk
		return acceptJoinRequest("token-${game.creatorEmail}", gameId, JoinRequestIds(gameId).of(email))
	}

	private fun requestToJoin(token: String?, gameId: String): RestTestClient.ResponseSpec {
		val request = restTestClient.post().uri("/api/games/{id}/join-requests", gameId)
		token?.let { request.header("Authorization", "Bearer $it") }
		return request.exchange()
	}

	private fun acceptJoinRequest(token: String, gameId: String, requestId: String): RestTestClient.ResponseSpec =
		restTestClient.post().uri("/api/games/{id}/join-requests/{r}/accept", gameId, requestId)
			.header("Authorization", "Bearer $token").exchange()

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
