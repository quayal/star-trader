package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.battle.BattleReport
import dev.adriangrzebyk.trader.game.battle.SalvoOutcome
import dev.adriangrzebyk.trader.game.battle.SalvoResult
import dev.adriangrzebyk.trader.game.battle.SalvoRolls
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// Creator, 2026-11-19: a report keeps each salvo's rolls; one saved before that loads without them.
class BattleReportItemTest {

	private val report = BattleReport(
		StarSystem.TAU_CETI,
		2,
		listOf(
			SalvoResult("Wayfarer", "ada@example.com", "Meridian", "grace@example.com", true, 2, SalvoOutcome.DESTROYED, SalvoRolls(6, 4, 2, 1)),
			SalvoResult(
				"Lantern",
				"ada@example.com",
				"Meridian",
				"grace@example.com",
				false,
				1,
				SalvoOutcome.AWAITING_PLACEMENT,
				SalvoRolls(5, 3, 2, null)
			),
			SalvoResult("Meridian", "grace@example.com", "Lantern", "ada@example.com", false, 0, SalvoOutcome.BROKE_AWAY, null)
		)
	)

	@Test
	fun shouldReadBackEachSalvosRolls() {
		assertThat(BattleReportItem(BattleReportItem(report).attributes).toBattleReport()).isEqualTo(report)
	}

	@Test
	fun shouldReadAReportSavedBeforeRollsWereKeptWithNone() {
		val attributes = BattleReportItem(report).attributes
		val withoutRolls = attributes + (
			"results" to AttributeValue.fromL(attributes.getValue("results").l().map { AttributeValue.fromM(it.m() - "rolls") })
			)

		assertThat(BattleReportItem(withoutRolls).toBattleReport().results.map { it.rolls }).containsOnlyNulls()
	}
}
