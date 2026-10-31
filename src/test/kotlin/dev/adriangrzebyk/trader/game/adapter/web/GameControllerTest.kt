package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

// Blank the Resend settings so a developer's RESEND_* variables don't send real email to example.com.
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["RESEND_API_KEY=", "RESEND_FROM_ADDRESS="],
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
		assertThat(game.players).containsExactly(PlayerResponseDto("creator@example.com", "Adrian", "Adrian Star Traders"))
		assertThat(gameStore.find(game.id)!!.toResponseDto()).isEqualTo(game)
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
			PlayerResponseDto("host@example.com", "Adrian", "Adrian Star Traders"),
			PlayerResponseDto("guest@example.com", "Grace", "Grace Freight"),
		)
		assertThat(joined.status).isEqualTo("LOBBY")
		assertThat(gameStore.find(game.id)!!.toResponseDto()).isEqualTo(joined)
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
		val host = Player("started-host@example.com", "Host", "Host Corp")
		gameStore.create(Game("started-game", 20, GameStatus.RUNNING, host.email, listOf(host)))

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

		assertThat(seenByCreator).isEqualTo(gameStore.find(game.id)!!.toResponseDto())
		assertThat(seenByCreator.players.map { it.email }).containsExactly("poll-host@example.com", "poll-guest@example.com")
		assertThat(seenByJoiner).isEqualTo(seenByCreator)
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
	fun shouldRejectPollingWithoutASession() {
		val game = createdGame(signIn("poll-open-door@example.com"))

		pollGame(null, game.id).expectStatus().isUnauthorized
		pollGame("not-a-real-token", game.id).expectStatus().isUnauthorized
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
