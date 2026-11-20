package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.Notice
import dev.adriangrzebyk.trader.game.NoticeKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Playtest 2026-11-20: one summary of a battle, from the tries that started it to where the survivors went.
class BattleSummaryTest {

	private fun notice(kind: NoticeKind, round: Int? = null, system: StarSystem = TAU_CETI) = Notice(kind, system, emptyList(), round, null)

	private fun line(text: String, kind: NoticeKind? = null, round: Int? = null) =
		if (kind == null) LogEntry.event(3, text) else LogEntry.notice(3, text, notice(kind, round))

	@Test
	fun shouldGroupTheBattlesLinesIntoHowItStartedEachRoundAndTheAftermath() {
		val log = listOf(
			LogEntry.notice(3, "An older battle at Mu Herculis", notice(NoticeKind.BATTLE_STARTED, system = MU_HERCULIS)),
			line("Vega Lines jumped Starfarer to Tau Ceti"),
			line("Meridian tries to intercept Starfarer: held", NoticeKind.INTERCEPTION),
			line("Vega Lines: reputation −7 (trying to intercept)", NoticeKind.INTERCEPTION),
			line("Battle at Tau Ceti: Starfarer, Meridian", NoticeKind.BATTLE_STARTED),
			line("Meridian fires at Starfarer: 3 hits", NoticeKind.BATTLE_ROUND, 1),
			line("Starfarer is destroyed", NoticeKind.SHIP_DESTROYED),
			line("Starfarer fires at Meridian: 1 hit", NoticeKind.BATTLE_ROUND, 2),
			line("The battle at Tau Ceti is over", NoticeKind.BATTLE_OVER),
			line("Meridian landed at the Port of Tau Ceti")
		)

		assertThat(BattleSummary.of(log, TAU_CETI).sections).containsExactly(
			BattleSummarySection(
				"How it started",
				listOf(
					"Meridian tries to intercept Starfarer: held",
					"Vega Lines: reputation −7 (trying to intercept)",
					"Battle at Tau Ceti: Starfarer, Meridian"
				)
			),
			BattleSummarySection("Round 1", listOf("Meridian fires at Starfarer: 3 hits", "Starfarer is destroyed")),
			BattleSummarySection("Round 2", listOf("Starfarer fires at Meridian: 1 hit")),
			BattleSummarySection("Aftermath", listOf("The battle at Tau Ceti is over", "Meridian landed at the Port of Tau Ceti"))
		)
	}

	@Test
	fun shouldTellABattleWhoseStartWasntLoggedFromItsFirstMoment() {
		val log = listOf(
			line("Vega Lines raised business connections"),
			line("Meridian fires at Starfarer: 3 hits", NoticeKind.BATTLE_ROUND, 1),
			line("The battle at Tau Ceti is over", NoticeKind.BATTLE_OVER)
		)

		assertThat(BattleSummary.of(log, TAU_CETI).sections.map { it.title }).containsExactly("Round 1", "Aftermath")
	}
}
