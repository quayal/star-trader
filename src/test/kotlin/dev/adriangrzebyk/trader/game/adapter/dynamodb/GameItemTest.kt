package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

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
					Ship(name = "Wayfarer", system = StarSystem.TAU_CETI, location = ShipLocation.PORT),
					Ship(name = "Lantern", system = StarSystem.BETA_HYDRI, location = ShipLocation.SPACE)
				)
			),
			Player(
				email = "guest@example.com",
				name = "Guest",
				corporationName = "Guest Freight",
				money = 275,
				ships = listOf(
					Ship(name = "Meridian", system = StarSystem.GAMMA_LEPORIS, location = ShipLocation.PLANET),
					Ship(name = "Halcyon", system = StarSystem.TAU_CETI, location = ShipLocation.PORT)
				)
			)
		),
		turn = Turn(number = 1, phase = TurnPhase.BIDDING, doneBidding = emptyList()),
		log = listOf("Game started: 2 players, 20 turns", "Starting positions: Host Corp — Wayfarer at Tau Ceti")
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
	fun shouldStoreTheGameAsNativeAttributesWithNestedMapsAndLists() {
		val item = GameItem(startedGame, version = 0).attributes

		assertThat(item.keys).containsExactlyInAnyOrder("id", "turns", "status", "creatorEmail", "players", "turn", "log", "version")
		assertThat(item.getValue("status").s()).isEqualTo("RUNNING")
		assertThat(item.getValue("turn").m()).isEqualTo(TurnItem(startedGame.turn!!).attributes)
		assertThat(item.getValue("log").l().map { it.s() }).isEqualTo(startedGame.log)
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
