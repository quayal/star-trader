package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Dice
import dev.adriangrzebyk.trader.game.StartingSystemPicker
import dev.adriangrzebyk.trader.game.adapter.dynamodb.DynamoDbLocal
import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
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
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistrar
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.concurrent.atomic.AtomicInteger

/**
 * One whole game over HTTP, start to Game over, with every phase of every turn: the smoke test that the pieces work
 * together (the other tests each prove one rule). It follows the path the creator asked for: a factory's output is
 * collected into a warehouse one turn, loaded onto a ship in port the next, carried to another system and sold there.
 * Dice and starting systems are fixed, so every step and amount is known.
 */
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["RESEND_API_KEY=", "RESEND_FROM_ADDRESS="]
)
class WholeGameTest {

	@LocalServerPort
	private var port: Int = 0

	@Autowired
	private lateinit var sessionStore: SessionStore

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

	@TestConfiguration
	class FixedGame {

		@Bean
		fun useDynamoDbLocal() = DynamicPropertyRegistrar { registry ->
			registry.add("star-trader.dynamodb.endpoint-override") { DynamoDbLocal.endpoint }
			registry.add("star-trader.dynamodb.region") { DynamoDbLocal.REGION }
			registry.add("star-trader.dynamodb.table-name") { "games-whole-game-test" }
		}

		/** Every 2d6 is a 7; d2 alternates 2, 1, so with rolls taken in join order the host never ties the guest. */
		@Bean
		@Primary
		fun fixedDice(): Dice = object : Dice {
			private val rolls = AtomicInteger()

			override fun d2(): Int = if (rolls.getAndIncrement() % 2 == 0) 2 else 1

			override fun twoD6(): Int = 7

			override fun d6(): Int = 3
		}

		/** The host starts at Tau Ceti and Mu Herculis, the guest at Gamma Leporis and Beta Hydri. */
		@Bean
		@Primary
		fun fixedStart(): StartingSystemPicker = object : StartingSystemPicker {
			private val picks = AtomicInteger()

			override fun pickTwoDifferentSystems(): Pair<StarSystem, StarSystem> = if (picks.getAndIncrement() % 2 ==
				0
			) {
				StarSystem.TAU_CETI to StarSystem.MU_HERCULIS
			} else {
				StarSystem.GAMMA_LEPORIS to StarSystem.BETA_HYDRI
			}
		}
	}

	@Test
	fun shouldPlayAThreeTurnGameWhereOutputIsStoredLoadedCarriedAndSold() {
		val host = signIn("whole-host@example.com", "Ada", "Orion Freight")
		val guest = signIn("whole-guest@example.com", "Grace", "Vega Lines")
		val gameId = post(host, "/api/games", """{"turns": 3}""").id
		post(guest, "/api/games/$gameId/join")
		val started = post(host, "/api/games/$gameId/start")
		assertThat(started.players.first().ships.map { it.system }).containsExactly("TAU_CETI", "MU_HERCULIS")
		val wayfarer = started.players.first().ships.first().name

		// Turn 1: nobody trades; in Investment the host builds a warehouse and a 3-unit ISO factory at Tau Ceti.
		bidPickAndFinishJumping(host, guest, gameId)
		resolveEveryGood(host, gameId)
		post(host, "/api/games/$gameId/investment/warehouse-blocks", """{"system": "TAU_CETI", "blocks": 1}""")
		val built = post(host, "/api/games/$gameId/investment/factory-units", """{"system": "TAU_CETI", "goods": "ISO", "units": 3}""")
		assertThat(built.players.first().factories).containsExactly(FactoryResponseDto("TAU_CETI", "ISO", 3))
		assertThat(doneInvesting(host, guest, gameId).turn!!.number).isEqualTo(2)

		// Turn 2: the factory's first output goes into the warehouse (R47, R48).
		bidPickAndFinishJumping(host, guest, gameId)
		resolveEveryGood(host, gameId)
		val collected = post(
			host,
			"/api/games/$gameId/investment/collect",
			"""{"placements": [{"system": "TAU_CETI", "goods": "ISO", "ship": null, "units": 3}]}"""
		)
		assertThat(collected.you!!.warehouses.single().cargo).isEqualTo(mapOf("ISO" to 3))
		assertThat(doneInvesting(host, guest, gameId).turn!!.number).isEqualTo(3)

		// Turn 3, Bidding: last turn's output is loaded onto the ship in port (R58), and offered for sale at Mu Herculis.
		val loaded = post(host, "/api/games/$gameId/cargo/moves", """{"ship": "$wayfarer", "goods": "ISO", "units": 3, "to": "SHIP"}""")
		assertThat(loaded.you!!.ships.first().cargo).isEqualTo(mapOf("ISO" to 3))
		assertThat(loaded.you!!.warehouses.single().cargo).isEmpty()
		val isoAtMu = loaded.board.single { it.id == "MU_HERCULIS" }.market.single { it.goods == "ISO" }.price
		val price = maxOf(1, isoAtMu - 6) // far enough under the market that it allows more than the 3 units held (R15)
		post(host, "/api/games/$gameId/offers", """{"system": "MU_HERCULIS", "goods": "ISO", "type": "SELL", "price": $price}""")
		post(host, "/api/games/$gameId/bidding/bid", """{"amount": 1}""", put = true)
		post(guest, "/api/games/$gameId/bidding/done")
		post(host, "/api/games/$gameId/bidding/done")

		// Ship-jumps: the ship carries it to Mu Herculis and lands at the Port, where it can trade (R23).
		post(host, "/api/games/$gameId/ship-jumps/turn-order", """{"place": 1}""")
		post(host, "/api/games/$gameId/ship-jumps/jumps", """{"ship": "$wayfarer", "destination": "MU_HERCULIS"}""")
		post(host, "/api/games/$gameId/ship-jumps/landing", """{"location": "PORT"}""")
		post(host, "/api/games/$gameId/ship-jumps/finish")
		val trading = post(guest, "/api/games/$gameId/ship-jumps/finish")
		assertThat(trading.turn!!.phase).isEqualTo("TRADING")
		val moneyBefore = poll(host, gameId).you!!.money

		// Trading: the market reaches the offer, and the host sells all 3 from the ship.
		resolveEveryGood(host, gameId) { awaited ->
			assertThat(awaited.you!!.tradeMax).isEqualTo(3)
			post(host, "/api/games/$gameId/trading/trade", """{"amount": 3, "ships": [{"name": "$wayfarer", "units": 3}]}""")
		}
		val sold = poll(host, gameId)
		assertThat(sold.turn!!.phase).isEqualTo("INVESTMENT")
		assertThat(sold.you!!.ships.first().cargo).isEmpty()
		assertThat(sold.you!!.money).isEqualTo(moneyBefore + 3 * price)
		assertThat(sold.log.map { it.text }).anyMatch { it.endsWith(": sold 3 u at $price HT each, received ${3 * price} HT") }

		// Investment closes the last turn: the game is over, and everyone sees everyone's money (R39).
		post(host, "/api/games/$gameId/investment/collect", """{"placements": []}""")
		val over = doneInvesting(host, guest, gameId)
		assertThat(over.status).isEqualTo("FINISHED")
		assertThat(over.result!!.standings.map { it.id }).containsExactlyInAnyOrder("p1", "p2")
		assertThat(over.result!!.standings.single { it.id == "p1" }.money).isEqualTo(sold.you!!.money)
		assertThat(over.log.last().text).startsWith("Game over: ")
		assertThat(poll(guest, gameId).result).isEqualTo(over.result)
	}

	/** A turn's Bidding and Ship-jumps with nothing offered and nobody jumping; the host bids 1 HT, so is first in initiative. */
	private fun bidPickAndFinishJumping(host: String, guest: String, gameId: String) {
		post(host, "/api/games/$gameId/bidding/bid", """{"amount": 1}""", put = true)
		post(guest, "/api/games/$gameId/bidding/done")
		val closed = post(host, "/api/games/$gameId/bidding/done")
		assertThat(closed.turn!!.initiative).containsExactly("p1", "p2")
		post(host, "/api/games/$gameId/ship-jumps/turn-order", """{"place": 1}""")
		post(host, "/api/games/$gameId/ship-jumps/finish")
		assertThat(post(guest, "/api/games/$gameId/ship-jumps/finish").turn!!.phase).isEqualTo("TRADING")
	}

	/** The host, first in initiative, resolves every good; [onTrade] answers when the market reaches an offer of theirs. */
	private fun resolveEveryGood(host: String, gameId: String, onTrade: (GameResponseDto) -> Unit = { error("No trade was expected") }) {
		repeat(40) {
			val game = poll(host, gameId)
			if (game.turn!!.phase != "TRADING") {
				return
			}
			if (game.turn.resolution?.awaitingTrade != null) onTrade(game) else post(host, "/api/games/$gameId/trading/resolve-next")
		}
		error("Trading didn't finish")
	}

	private fun doneInvesting(host: String, guest: String, gameId: String): GameResponseDto {
		post(guest, "/api/games/$gameId/investment/done")
		return post(host, "/api/games/$gameId/investment/done")
	}

	private fun signIn(email: String, name: String, corporationName: String): String {
		val token = "token-$email"
		sessionStore.store(Session(token, name, corporationName, email))
		return token
	}

	private fun poll(token: String, gameId: String): GameResponseDto =
		restTestClient.get().uri("/api/games/$gameId").header("Authorization", "Bearer $token").exchange()
			.expectStatus().isOk.expectBody(GameResponseDto::class.java).returnResult().responseBody!!

	/** Sends an action and answers with the game it returns; anything but success fails the test with the server's message. */
	private fun post(token: String, path: String, body: String? = null, put: Boolean = false): GameResponseDto {
		val request = (if (put) restTestClient.put() else restTestClient.post()).uri(path).header("Authorization", "Bearer $token")
		val answer = (if (body == null) request else request.contentType(MediaType.APPLICATION_JSON).body(body)).exchange()
		return answer.expectStatus().is2xxSuccessful.expectBody(GameResponseDto::class.java).returnResult().responseBody!!
	}
}
