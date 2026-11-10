package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds data to Game extends this round trip in the same PR (engineering standards §12).
class GameItemTest {

	// Every field set to a non-default value, so a field the item drops can't round-trip by accident.
	private val startedGame = Game(
		id = "game-1",
		turns = 20,
		status = GameStatus.RUNNING,
		creatorEmail = "host@example.com",
		players = listOf(
			Player(
				email = "host@example.com",
				name = "Host",
				corporationName = "Host Corp",
				money = 300,
				ships = listOf(
					Ship(name = "Wayfarer", system = StarSystem.TAU_CETI, location = ShipLocation.PORT, cargo = emptyMap()),
					Ship(name = "Lantern", system = StarSystem.BETA_HYDRI, location = ShipLocation.SPACE, cargo = emptyMap())
				),
				marketPositions = emptyList(),
				pendingPositions = emptyList()
			),
			Player(
				email = "guest@example.com",
				name = "Guest",
				corporationName = "Guest Freight",
				money = 275,
				ships = listOf(
					Ship(name = "Meridian", system = StarSystem.GAMMA_LEPORIS, location = ShipLocation.PLANET, cargo = emptyMap()),
					Ship(name = "Halcyon", system = StarSystem.TAU_CETI, location = ShipLocation.PORT, cargo = emptyMap())
				),
				marketPositions = emptyList(),
				pendingPositions = emptyList()
			)
		),
		turn = Turn(
			number = 1,
			phase = TurnPhase.BIDDING,
			doneBidding = emptyList(),
			offers = emptyList(),
			bids = emptyList(),
			initiative = emptyList(),
			turnOrderPicks = emptyList(),
			jumpedShips = emptyList(),
			doneJumping = emptyList(),
			landingShip = null,
			tradingQueue = emptyList(),
			resolvedGoods = emptyList(),
			resolution = null
		),
		prices = MarketPrices.board().withPrice(MarketGood(StarSystem.MU_HERCULIS, Goods.ISO), 10),
		log = listOf(
			LogEntry(1, "Game started: 2 players, 20 turns"),
			LogEntry(1, "Starting positions: Host Corp — Wayfarer at Tau Ceti"),
			LogEntry(2, "Initiative order (bid + d2): Guest Freight → Host Corp")
		)
	)

	@Test
	fun shouldReadBackEveryFieldOfAStartedGame() {
		val item = GameItem(startedGame, version = 3)

		assertThat(GameItem(item.attributes).toGame()).isEqualTo(startedGame)
		assertThat(GameItem(item.attributes).version).isEqualTo(3)
	}

	@Test
	fun shouldReadBackALobbyGameThatHasNoTurnYet() {
		val lobby = Game.lobby("game-2", 12, Player.newcomer("host@example.com", "Host", "Host Corp"))

		assertThat(GameItem(GameItem(lobby, version = 0).attributes).toGame()).isEqualTo(lobby)
	}

	@Test
	fun shouldStoreEachPriceUnderItsGoodAndReadAGameSavedBeforePricesMovedAtTheBoardsPrices() {
		// E05-S05, R35: a game saved before then never moved a price (BACKWARD_COMPATIBILITY.md §7).
		val item = GameItem(startedGame, version = 0).attributes
		val muHerculisIso = item.getValue("prices").l().map { it.m() }.single {
			it.getValue("system").s() == "MU_HERCULIS" &&
				it.getValue("goods").s() == "ISO"
		}

		assertThat(muHerculisIso.keys).containsExactlyInAnyOrder("system", "goods", "price")
		assertThat(muHerculisIso.getValue("price")).isEqualTo(AttributeValue.fromN("10"))
		assertThat(GameItem(item - "prices").toGame().prices).isEqualTo(MarketPrices.board())
	}

	@Test
	fun shouldStoreTheGameAsNativeAttributesWithNestedMapsAndLists() {
		val item = GameItem(startedGame, version = 0).attributes

		assertThat(item.keys).containsExactlyInAnyOrder("id", "turns", "status", "creatorEmail", "players", "turn", "prices", "log", "version")
		assertThat(item.getValue("status").s()).isEqualTo("RUNNING")
		assertThat(item.getValue("turn").m()).isEqualTo(TurnItem(startedGame.turn!!).attributes)
		assertThat(item.getValue("log").l()).isEqualTo(startedGame.log.map { LogEntryItem(it).attribute })
		assertThat(item.getValue("players").l().map { it.m() }).isEqualTo(startedGame.players.map { PlayerItem(it).attributes })
	}

	@Test
	fun shouldStoreAGameThatHasNotStartedWithANullTurn() {
		val lobby = Game.lobby("game-2", 12, Player.newcomer("host@example.com", "Host", "Host Corp"))

		assertThat(GameItem(lobby, version = 0).attributes.getValue("turn").nul()).isTrue()
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredItemIsIncomplete() {
		val item = GameItem(startedGame, version = 0).attributes - "log"

		assertThatThrownBy { GameItem(item).toGame() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("log")
	}
}
