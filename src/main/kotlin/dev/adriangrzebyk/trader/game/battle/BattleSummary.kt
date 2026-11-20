package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.NoticeKind

/**
 * A finished battle told in one place (playtest, 2026-11-20): what started it, each round, and the aftermath, with every
 * reputation change, destruction and landing on the way. Built from the public log, so it holds nothing new.
 */
data class BattleSummary(val sections: List<BattleSummarySection>) {

	companion object {
		private const val STARTED = "How it started"
		private const val AFTERMATH = "Aftermath"

		/**
		 * The summary of the battle at [system] that has just finished, from [log]: its lines start at the interception tries
		 * right before its BATTLE_STARTED moment (none when a ship stayed in Space, R80) and run to the end of the log.
		 */
		fun of(log: List<LogEntry>, system: StarSystem): BattleSummary {
			val started = log.indexOfLast { it.notice?.kind == NoticeKind.BATTLE_STARTED && it.notice.system == system }
				.takeIf { it >= 0 }
				// A battle whose start wasn't logged (set up directly) is told from its first logged moment there.
				?: log.indexOfFirst { it.notice?.system == system && it.notice.kind != NoticeKind.SABOTAGE }.takeIf { it >= 0 }
				?: log.size
			var from = started
			while (from > 0 && log[from - 1].notice?.let { it.kind == NoticeKind.INTERCEPTION && it.system == system } == true) {
				from--
			}
			val sections = mutableListOf<BattleSummarySection>()
			var title = STARTED
			for (entry in log.drop(from)) {
				title = when {
					title == AFTERMATH -> AFTERMATH
					entry.notice?.kind == NoticeKind.BATTLE_ROUND -> "Round ${entry.notice.round}"
					entry.notice?.kind == NoticeKind.BATTLE_OVER -> AFTERMATH
					else -> title
				}
				val last = sections.lastOrNull()
				if (last?.title == title) {
					sections[sections.lastIndex] = last.with(entry.text)
				} else {
					sections += BattleSummarySection(title, listOf(entry.text))
				}
			}
			return BattleSummary(sections)
		}
	}
}

/** One part of a [BattleSummary]: its [title] ("Round 2") and its log [lines], in order. */
data class BattleSummarySection(val title: String, val lines: List<String>) {

	/** This section with [line] added at the end. */
	fun with(line: String): BattleSummarySection = copy(lines = lines + line)
}
