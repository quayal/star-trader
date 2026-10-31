package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.FakeStartingSystemPicker
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.InvalidGameOperationException
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DynamoDbGameStoreTest {

	private val creator = Player.newcomer("creator@example.com", "Creator", "Creator Corp")
	private val game = Game.lobby("game-1", 20, creator)

	// A fresh table per test, so tests sharing the container can't see each other's games.
	private val tableName = "games-${UUID.randomUUID()}"
	private lateinit var client: DynamoDbClient
	private lateinit var store: DynamoDbGameStore

	@BeforeEach
	fun setUp() {
		DynamoDbLocal.assumeAvailable()
		client = DynamoDbLocal.client()
		GamesTable.createIfMissing(client, tableName)
		store = DynamoDbGameStore(client, tableName)
	}

	@AfterEach
	fun tearDown() {
		if (::client.isInitialized) client.close()
	}

	@Test
	fun shouldFindACreatedGameById() {
		store.create(game)

		assertThat(store.find("game-1")).isEqualTo(game)
		assertThat(store.find("game-2")).isNull()
	}

	@Test
	fun shouldStillFindTheGameThroughANewStoreAsAfterARestart() {
		store.create(game)
		store.update("game-1") { it.addPlayer(Player.newcomer("joiner@example.com", "Joiner", "Joiner Corp")) }

		DynamoDbLocal.client().use { freshClient ->
			val restarted = DynamoDbGameStore(freshClient, tableName)

			assertThat(restarted.find("game-1")!!.players.map { it.email })
				.containsExactly("creator@example.com", "joiner@example.com")
		}
	}

	@Test
	fun shouldStillHaveAStartedGameWithMoneyShipsTurnAndLogAfterARestart() {
		store.create(game)
		store.update("game-1") { it.addPlayer(Player.newcomer("joiner@example.com", "Joiner", "Joiner Corp")) }
		val picker = FakeStartingSystemPicker(
			StarSystem.TAU_CETI to StarSystem.BETA_HYDRI,
			StarSystem.GAMMA_LEPORIS to StarSystem.MU_HERCULIS,
		)
		val started = store.update("game-1") { it.start(creator.email, picker) }

		DynamoDbLocal.client().use { freshClient ->
			val restarted = DynamoDbGameStore(freshClient, tableName).find("game-1")!!

			assertThat(restarted).isEqualTo(started)
			assertThat(restarted.status).isEqualTo(GameStatus.RUNNING)
			assertThat(restarted.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
			assertThat(restarted.players.map { it.money }).containsOnly(Game.STARTING_MONEY)
			assertThat(restarted.players.flatMap { it.ships }.map { it.system })
				.containsExactly(StarSystem.TAU_CETI, StarSystem.BETA_HYDRI, StarSystem.GAMMA_LEPORIS, StarSystem.MU_HERCULIS)
			assertThat(restarted.log).hasSize(2)
		}
	}

	@Test
	fun shouldKeepGamesSeparateById() {
		val other = game.copy(id = "game-2", turns = 5)
		store.create(game)
		store.create(other)

		assertThat(store.find("game-1")).isEqualTo(game)
		assertThat(store.find("game-2")).isEqualTo(other)
	}

	@Test
	fun shouldRejectASecondGameWithTheSameId() {
		store.create(game)

		assertThatThrownBy { store.create(game.copy(turns = 5)) }.isInstanceOf(IllegalStateException::class.java)
		assertThat(store.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldUpdateAGameAndBumpItsVersion() {
		store.create(game)
		val joiner = Player.newcomer("joiner@example.com", "Joiner", "Joiner Corp")

		val updated = store.update("game-1") { it.addPlayer(joiner) }

		assertThat(updated.players).containsExactly(creator, joiner)
		assertThat(store.find("game-1")).isEqualTo(updated)
		assertThat(storedVersion("game-1")).isEqualTo(1)
	}

	@Test
	fun shouldLeaveTheGameUnchangedWhenTheChangeIsRefused() {
		store.create(game)

		assertThatThrownBy { store.update("game-1") { it.addPlayer(creator) } }
			.isInstanceOf(InvalidGameOperationException::class.java)
		assertThat(store.find("game-1")).isEqualTo(game)
		assertThat(storedVersion("game-1")).isEqualTo(0)
	}

	@Test
	fun shouldFailToUpdateAnUnknownGame() {
		assertThatThrownBy { store.update("missing") { it } }.isInstanceOf(NoSuchElementException::class.java)
	}

	@Test
	fun shouldApplyConcurrentUpdatesWithoutLosingAny() {
		store.create(game)
		val executor = Executors.newFixedThreadPool(5)

		val updates = (1..5).map { n ->
			executor.submit { store.update("game-1") { it.addPlayer(Player.newcomer("p$n@example.com", "P$n", "Corp $n")) } }
		}
		updates.forEach { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		assertThat(store.find("game-1")!!.players).hasSize(6)
		assertThat(storedVersion("game-1")).isEqualTo(5)
	}

	private fun storedVersion(id: String): Long = GameItemMapper.version(
		client.getItem {
			it.tableName(tableName).key(mapOf("id" to AttributeValue.fromS(id))).consistentRead(true)
		}.item(),
	)
}
