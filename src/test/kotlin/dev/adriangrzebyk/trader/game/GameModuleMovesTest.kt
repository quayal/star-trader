package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R122, R123: moving modules happens in Investment, like refits (R65), and is private.
class GameModuleMovesTest {

	private val fixture = OpportunityGame()

	/** [game] with Kim's ships both in Mu Herculis Port, Corvid carrying a Dogfight in place of a Cargo, and a warehouse there. */
	private fun prepared(game: Game): Game = fixture.with(game, KIM) { kim ->
		kim.copy(
			ships = kim.ships.map {
				val docked = it.copy(system = MU_HERCULIS, location = ShipLocation.PORT).withModuleTakenOff(Module.CARGO)
				if (it.name == "Corvid") docked.withModuleFitted(Module.DOGFIGHT) else docked
			},
			warehouses = listOf(Warehouse(MU_HERCULIS, 1, emptyMap()))
		)
	}

	private fun kim(game: Game): Player = fixture.playerOf(game, KIM)

	@Test
	fun shouldMoveModulesInInvestmentWithoutALogLine() {
		val investing = prepared(fixture.opportunity.copy(turn = fixture.opportunity.turn!!.copy(phase = TurnPhase.INVESTMENT)))

		val stored = investing.storeModule(KIM, "Bright Reach", Module.CARGO)
		val fitted = stored.fitStoredModule(KIM, "Bright Reach", Module.CARGO)
		val moved = investing.moveMilitaryModule(KIM, "Corvid", "Bright Reach", Module.DOGFIGHT)

		assertThat(kim(stored).warehouseAt(MU_HERCULIS)!!.modules).containsExactly(Module.CARGO)
		assertThat(kim(fitted).warehouseAt(MU_HERCULIS)!!.modules).isEmpty()
		assertThat(kim(fitted).ship("Bright Reach")!!.modules).containsExactlyInAnyOrderElementsOf(kim(investing).ship("Bright Reach")!!.modules)
		assertThat(kim(moved).ship("Bright Reach")!!.modules).contains(Module.DOGFIGHT)
		assertThat(listOf(stored, fitted, moved).map { it.log }).allMatch { it == investing.log }
	}

	@Test
	fun shouldRefuseOutsideInvestment() {
		assertThatThrownBy { prepared(fixture.opportunity).storeModule(KIM, "Bright Reach", Module.CARGO) }
			.hasMessage("Investment isn't open.")
	}
}
