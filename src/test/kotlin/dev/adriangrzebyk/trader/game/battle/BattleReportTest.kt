package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R75–R78: what a battle round's salvos did, as each player is told.
class BattleReportTest {

	private val ada = "ada@example.com"
	private val grace = "grace@example.com"
	private val kim = "kim@example.com"

	private val report = BattleReport(
		TAU_CETI,
		2,
		listOf(
			SalvoResult("Wayfarer", ada, "Meridian", grace, false, 2, SalvoOutcome.AWAITING_PLACEMENT),
			SalvoResult("Lantern", ada, "Halcyon", grace, true, 3, SalvoOutcome.AWAITING_PLACEMENT),
			SalvoResult("Meridian", grace, "Wayfarer", ada, false, 0, SalvoOutcome.NO_DAMAGE),
			SalvoResult("Corvid", kim, "Halcyon", grace, false, 1, SalvoOutcome.AWAITING_PLACEMENT)
		)
	)

	@Test
	fun shouldTellEachPlayerOnlyTheSalvosTheirShipsFiredOrTook() {
		assertThat(report.reportFor(ada)!!.results.map { it.firer }).containsExactly("Wayfarer", "Lantern", "Meridian")
		assertThat(report.reportFor(kim)!!.results.map { it.firer }).containsExactly("Corvid")
		assertThat(report.reportFor("nobody@example.com")).isNull()
	}

	@Test
	fun shouldSettleTheSalvosOnShipsWhoseHitsWerePlaced() {
		// R76: Grace placed hits on Meridian and Halcyon; Halcyon couldn't take them.
		val placed = report.hitsPlaced(setOf("Meridian", "Halcyon"), setOf("Halcyon"))

		assertThat(placed.results.map { it.outcome }).containsExactly(
			SalvoOutcome.PLACED,
			SalvoOutcome.DESTROYED_WHEN_PLACED,
			SalvoOutcome.NO_DAMAGE,
			SalvoOutcome.DESTROYED_WHEN_PLACED
		)
	}

	@Test
	fun shouldLeaveOtherShipsSalvosWaiting() {
		val placed = report.hitsPlaced(setOf("Wayfarer"), emptySet())

		assertThat(placed).isEqualTo(report)
	}
}
