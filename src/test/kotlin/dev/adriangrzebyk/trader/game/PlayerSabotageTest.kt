package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R99: what a sabotage's effect does to its target's owner.
class PlayerSabotageTest {

	private val grace = Player.newcomer(GRACE, "Grace", "Vega Lines").startWith(
		300,
		listOf(Ship.emptyAt("Meridian", TAU_CETI, ShipLocation.PORT).load(Goods.ISO, 3)),
		0
	).copy(
		factories = listOf(Factory(TAU_CETI, Goods.ISO, 2, 1)),
		warehouses = listOf(Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 4, Goods.POLY to 3))),
		agents = listOf(RecruitedAgent(Agent.JB, AgentPosition.Aboard("Meridian")))
	)

	private val factory = SabotageTarget.Factory(GRACE, TAU_CETI, Goods.ISO)
	private val warehouse = SabotageTarget.Warehouse(GRACE, TAU_CETI)
	private val ship = SabotageTarget.Ship(GRACE, TAU_CETI, "Meridian")

	@Test
	fun shouldChangeNothingAndLogNothingOnAnEffectOfZero() {
		listOf(factory, warehouse, ship).forEach {
			assertThat(grace.sufferSabotage(it, 0)).isEqualTo(SabotageOutcome(grace, emptyMap(), null, false))
		}
	}

	@Test
	fun shouldAddTheTurnsToADisabledFactory() {
		val outcome = grace.sufferSabotage(factory, 3)

		assertThat(outcome.owner.factoryAt(TAU_CETI, Goods.ISO)!!.disabledTurns).isEqualTo(4)
		assertThat(outcome.lost).isEmpty()
		assertThat(outcome.logged).isEqualTo(3)
	}

	@Test
	fun shouldEmptyAWarehouseHoldingNoMoreThanTheEffectAndLogWhatItHeld() {
		val outcome = grace.sufferSabotage(warehouse, 9)

		assertThat(outcome.owner.warehouseAt(TAU_CETI)!!.cargo).isEmpty()
		assertThat(outcome.lost).isEqualTo(mapOf(Goods.ISO to 4, Goods.POLY to 3))
		assertThat(outcome.logged).isEqualTo(7)
	}

	@Test
	fun shouldLeaveAFullerWarehouseForTheSaboteurToChoose() {
		val outcome = grace.sufferSabotage(warehouse, 3)

		assertThat(outcome.owner).isEqualTo(grace)
		assertThat(outcome.lost).isNull()
		assertThat(outcome.logged).isEqualTo(3)
	}

	@Test
	fun shouldLogNothingForAnEmptyWarehouse() {
		val empty = grace.copy(warehouses = listOf(Warehouse(TAU_CETI, 1, emptyMap())))

		assertThat(empty.sufferSabotage(warehouse, 5)).isEqualTo(SabotageOutcome(empty, emptyMap(), null, false))
	}

	@Test
	fun shouldLeaveTheHitsOfASurvivableSabotageToTheOwner() {
		val outcome = grace.sufferSabotage(ship, 2)

		assertThat(outcome.owner).isEqualTo(grace)
		assertThat(outcome.lost).isNull()
		assertThat(outcome.destroyed).isFalse()
	}

	@Test
	fun shouldDestroyAShipThatCannotSurviveTheHitsWithItsCargoAndStepItsAgentsOff() {
		// R107: destroyed outside Space, its agents step off into the Port and survive
		val hits = grace.ship("Meridian")!!.hitsSurvivable() + 1

		val outcome = grace.sufferSabotage(ship, hits)

		assertThat(outcome.destroyed).isTrue()
		assertThat(outcome.owner.ship("Meridian")).isNull()
		assertThat(outcome.lost).isEqualTo(mapOf(Goods.ISO to 3))
		assertThat(outcome.owner.agents).containsExactly(RecruitedAgent(Agent.JB, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT)))
	}

	private companion object {
		const val GRACE = "grace@example.com"
	}
}
