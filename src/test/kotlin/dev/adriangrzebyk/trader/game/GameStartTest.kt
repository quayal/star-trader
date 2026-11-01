package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameStartTest {

	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val lobby = Game.lobby("game-1", 20, creator).addPlayer(joiner)
	private val picker = FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to TAU_CETI)

	@Test
	fun shouldGiveEveryPlayer300HtAndTwoShipsAtThePortsOfTheirPickedSystems() {
		val started = lobby.start("creator@example.com", picker)

		assertThat(started.players.map { it.money }).containsOnly(300)
		assertThat(started.players[0].ships).containsExactly(
			Ship("Wayfarer", TAU_CETI, ShipLocation.PORT),
			Ship("Lantern", BETA_HYDRI, ShipLocation.PORT)
		)
		assertThat(started.players[1].ships).containsExactly(
			Ship("Meridian", GAMMA_LEPORIS, ShipLocation.PORT),
			Ship("Halcyon", TAU_CETI, ShipLocation.PORT)
		)
	}

	@Test
	fun shouldMoveTheGameToTurnOneBidding() {
		val started = lobby.start("creator@example.com", picker)

		assertThat(started.status).isEqualTo(GameStatus.RUNNING)
		assertThat(started.turn).isEqualTo(Turn(1, TurnPhase.BIDDING))
	}

	@Test
	fun shouldLogTheStartAndEveryShipsStartingSystem() {
		val started = lobby.start("creator@example.com", picker)

		assertThat(started.log).containsExactly(
			"Game started: 2 players, 20 turns",
			"Starting positions: Orion Freight — Wayfarer at Tau Ceti, Lantern at Beta Hydri; " +
				"Vega Lines — Meridian at Gamma Leporis, Halcyon at Tau Ceti"
		)
	}

	@Test
	fun shouldNameEveryShipDifferentlyInAFullGame() {
		val full = (2..6).fold(Game.lobby("game-1", 20, creator)) { game, n ->
			game.addPlayer(Player.newcomer("p$n@example.com", "P$n", "Corp $n"))
		}
		val pairs = List(6) { SIGMA_DRACONIS to TAU_CETI }.toTypedArray()

		val names = full.start("creator@example.com", FakeStartingSystemPicker(*pairs)).players.flatMap { it.ships }.map { it.name }

		assertThat(names).hasSize(12).doesNotHaveDuplicates()
	}

	@Test
	fun shouldLetOnlyTheCreatorStart() {
		assertThatThrownBy { lobby.start("joiner@example.com", picker) }
			.isInstanceOf(NotTheCreatorException::class.java)
			.hasMessage("Only the creator can start the game")
	}

	@Test
	fun shouldNotStartWithFewerThanTwoPlayers() {
		assertThatThrownBy { Game.lobby("game-1", 20, creator).start("creator@example.com", picker) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("At least 2 players are needed to start")
	}

	@Test
	fun shouldNotStartTwice() {
		val started = lobby.start("creator@example.com", picker)

		assertThatThrownBy { started.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI)) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game has already started")
	}

	@Test
	fun shouldCloseJoiningOnceStarted() {
		val started = lobby.start("creator@example.com", picker)

		assertThatThrownBy { started.addPlayer(Player.newcomer("late@example.com", "Late", "Late Corp")) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game has already started")
	}
}
