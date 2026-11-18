package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.ConnectionsPlaced
import dev.adriangrzebyk.trader.game.FakeStartingSystemPicker
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameNotFoundException
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.InvalidGameOperationException
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.START_DICE
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.game.adapter.random.RandomDice
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
		GamesTable(client, tableName).createIfMissing()
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
	fun shouldNotTakeAnotherKindOfItemInTheTableForAGame() {
		client.putItem {
			it.tableName(tableName).item(mapOf("id" to AttributeValue.fromS("session#abc"), "email" to AttributeValue.fromS("a@example.com")))
		}

		assertThat(store.find("session#abc")).isNull()
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
			StarSystem.GAMMA_LEPORIS to StarSystem.MU_HERCULIS
		)
		val started = store.update("game-1") { it.start(creator.email, picker, START_DICE).let { ConnectionsPlaced(it).byEveryone() } }

		DynamoDbLocal.client().use { freshClient ->
			val restarted = DynamoDbGameStore(freshClient, tableName).find("game-1")!!

			assertThat(restarted).isEqualTo(started)
			assertThat(restarted.status).isEqualTo(GameStatus.RUNNING)
			assertThat(restarted.turn!!.phase).isEqualTo(TurnPhase.BIDDING)
			assertThat(restarted.players.map { it.money }).containsOnly(Game.STARTING_MONEY)
			assertThat(restarted.players.flatMap { it.ships }.map { it.system })
				.containsExactly(StarSystem.TAU_CETI, StarSystem.BETA_HYDRI, StarSystem.GAMMA_LEPORIS, StarSystem.MU_HERCULIS)
			assertThat(restarted.log).hasSize(6)
			assertThat(restarted.players.map { it.connections.business }).containsOnly(3)
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
		assertThatThrownBy { store.update("missing") { it } }.isInstanceOf(GameNotFoundException::class.java)
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

	@Test
	fun shouldCloseBiddingExactlyOnceWhenEveryPlayerSignalsDoneAtOnce() {
		val full = (2..6).fold(game) { lobby, n -> lobby.addPlayer(Player.newcomer("p$n@example.com", "P$n", "Corp $n")) }
		val pairs = List(6) { StarSystem.TAU_CETI to StarSystem.BETA_HYDRI }.toTypedArray()
		store.create(full.start("creator@example.com", FakeStartingSystemPicker(*pairs), START_DICE).let { ConnectionsPlaced(it).byEveryone() })
		val executor = Executors.newFixedThreadPool(6)

		val dice = RandomDice() // Random.Default: the signals roll from several threads
		val signals = full.players.map { player -> executor.submit { store.update("game-1") { it.signalDoneBidding(player.email, dice) } } }
		signals.forEach { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		// Every signal applied once, the last one closed Bidding, and nothing skipped past Ship-jumps (A04).
		val closed = store.find("game-1")!!
		assertThat(closed.turn?.phase).isEqualTo(TurnPhase.SHIP_JUMPS)
		assertThat(closed.turn?.doneBidding).isEmpty()
		assertThat(closed.turn?.initiative).containsExactlyInAnyOrderElementsOf(full.players.map { it.email })
		assertThat(closed.players.map { it.money }).allMatch { it == Game.STARTING_MONEY }
		assertThat(closed.log.filter { it.text.startsWith("Initiative order") }).hasSize(1)
		assertThat(storedVersion("game-1")).isEqualTo(6)
	}

	private val picker = FakeStartingSystemPicker(
		StarSystem.TAU_CETI to StarSystem.BETA_HYDRI,
		StarSystem.GAMMA_LEPORIS to StarSystem.MU_HERCULIS
	)

	/** A started two-player game: its log has the two lines of the start, both in turn 1. */
	private fun startedGame(): Game {
		store.create(game)
		store.update("game-1") { it.addPlayer(Player.newcomer("joiner@example.com", "Joiner", "Joiner Corp")) }
		return store.update("game-1") { it.start(creator.email, picker, START_DICE).let { ConnectionsPlaced(it).byEveryone() } }
	}

	private fun storedItem(id: String): Map<String, AttributeValue>? = client.getItem {
		it.tableName(tableName).key(mapOf("id" to AttributeValue.fromS(id))).consistentRead(true)
	}.takeIf { it.hasItem() }?.item()

	@Test
	fun shouldKeepEachTurnOfTheLogInAnItemOfItsOwn() {
		// the game item must not grow with the length of the game: DynamoDB refuses an item over 400 KB
		val started = startedGame()
		val later = store.update("game-1") { it.copy(log = it.log + LogEntry(2, "Turn 2 of 20 starts with Bidding")) }

		assertThat(storedItem("game-1")!!.keys).doesNotContain("log")
		assertThat(GameItem(storedItem("game-1")!!).logTurns()).containsExactly(1, 2)
		assertThat(LogTurnItem(storedItem("log#game-1#1")!!).entries()).isEqualTo(started.log)
		assertThat(LogTurnItem(storedItem("log#game-1#2")!!).entries()).containsExactly(LogEntry(2, "Turn 2 of 20 starts with Bidding"))
		assertThat(store.find("game-1")).isEqualTo(later)
	}

	@Test
	fun shouldNotTakeALogItemForAGame() {
		startedGame()

		assertThat(store.find("log#game-1#1")).isNull()
	}

	@Test
	fun shouldSaveAndReloadAGameWhoseLogIsFarOver400Kilobytes() {
		// 50 turns of 150 lines of 80 characters: 600 KB of text alone, which one item could never hold
		val started = startedGame()
		val line = "Vega Lines could buy up to 12 u: bought 10 u at 13 HT each, paid 130 HT ........"
		val long = started.log + (1..50).flatMap { turn -> List(150) { LogEntry(turn, "$line $it") } }.sortedBy { it.turn }
		assertThat(long.sumOf { it.text.length }).isGreaterThan(600_000)

		val saved = store.update("game-1") { it.copy(log = started.log + long.drop(started.log.size)) }
		val next = store.update("game-1") { it.copy(log = it.log + LogEntry(50, "Game over: Vega Lines wins with 2,310 HT")) }

		assertThat(saved.log).hasSize(7506)
		assertThat(store.find("game-1")).isEqualTo(next)
		assertThat(store.find("game-1")!!.log.last().text).startsWith("Game over")
		assertThat(GameItem(storedItem("game-1")!!).logTurns()).hasSize(50)
	}

	@Test
	fun shouldLoadAGameSavedWithItsLogInsideTheItemAndMoveItOutOnTheNextSave() {
		// BACKWARD_COMPATIBILITY.md §7: games saved before the log moved out keep working
		val started = startedGame()
		val older = GameItem(started, version = 7).attributes - "logTurns" +
			("log" to AttributeValue.fromL(started.log.map { LogEntryItem(it).attribute }))
		client.deleteItem { it.tableName(tableName).key(mapOf("id" to AttributeValue.fromS("log#game-1#1"))) }
		client.putItem { it.tableName(tableName).item(older) }

		assertThat(store.find("game-1")).isEqualTo(started)

		// a change that adds no log line still moves the log out
		val dice = RandomDice()
		val changed = store.update("game-1") { it.signalDoneBidding(creator.email, dice) }

		assertThat(changed.log).isEqualTo(started.log)
		assertThat(storedItem("game-1")!!.keys).doesNotContain("log")
		assertThat(LogTurnItem(storedItem("log#game-1#1")!!).entries()).isEqualTo(started.log)
		assertThat(store.find("game-1")).isEqualTo(changed)
		assertThat(storedVersion("game-1")).isEqualTo(8)
	}

	@Test
	fun shouldKeepEveryLogLineWhenChangesThatLogArriveAtOnce() {
		// each change adds a line to the same turn's log item, in a transaction that checks the game's version
		startedGame()
		val executor = Executors.newFixedThreadPool(5)

		val updates = (1..5).map { n -> executor.submit { store.update("game-1") { it.copy(log = it.log + LogEntry(1, "line $n")) } } }
		updates.forEach { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		assertThat(store.find("game-1")!!.log.map { it.text }.filter { it.startsWith("line") })
			.containsExactlyInAnyOrder("line 1", "line 2", "line 3", "line 4", "line 5")
	}

	private fun storedVersion(id: String): Long = GameItem(
		client.getItem {
			it.tableName(tableName).key(mapOf("id" to AttributeValue.fromS(id))).consistentRead(true)
		}.item()
	).version
}
