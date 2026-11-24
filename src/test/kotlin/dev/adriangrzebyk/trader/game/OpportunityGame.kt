package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI

/**
 * A three-player game for the Opportunity phase's tests (R102). Ada (Orion Freight: Wayfarer at Gamma Leporis, Lantern at
 * Mu Herculis), Grace (Vega Lines: Meridian at Sigma Draconis, Halcyon at Epsilon Eridani) and Kim (Kessler Cargo:
 * Bright Reach at Tau Ceti, Corvid at Beta Hydri). Initiative rolls 2, 1, 3, so the order is Kim, Ada, Grace.
 */
class OpportunityGame {

	val started: Game = Game.lobby("game-1", 20, Player.newcomer(ADA, "Ada", "Orion Freight"))
		.addPlayer(Player.newcomer(GRACE, "Grace", "Vega Lines"))
		.addPlayer(Player.newcomer(KIM, "Kim", "Kessler Cargo"))
		.start(ADA, START_DICE).let {
			StartingShips(
				FakeStartingSystemPicker(
					GAMMA_LEPORIS to MU_HERCULIS,
					SIGMA_DRACONIS to EPSILON_ERIDANI,
					TAU_CETI to BETA_HYDRI
				)
			).placedIn(it)
		}
		.let { ConnectionsPlaced(it).byEveryone() }

	/** [started] in turn 1's Trading. */
	val trading: Game = (1..3).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1, 3)) }

	/** [started] at the start of turn 1's Opportunity, Kim on turn. */
	val opportunity: Game = trading.advancePhase(FakeDice())

	/** [game] with [email]'s player changed by [change]. */
	fun with(game: Game, email: String, change: (Player) -> Player): Game =
		game.copy(players = game.players.map { if (it.email == email) change(it) else it })

	fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	companion object {
		const val ADA = "ada@example.com"
		const val GRACE = "grace@example.com"
		const val KIM = "kim@example.com"
	}
}
