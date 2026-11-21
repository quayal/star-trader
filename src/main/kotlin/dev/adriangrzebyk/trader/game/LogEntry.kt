package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.battle.BattleSummary

/**
 * One public event of the Game log (s04, D07), in the [turn] it happened in, so the log can be read by turn (S06-1).
 * Public by definition: it never carries an offer, a bid while Bidding is open, money or anything else confidential (R17,
 * R29, S06-3). [trade] is the trade the line tells of, for a player's trade history, and [notice] the interception or
 * battle moment it tells of, for the pop-up cards (creator, 2026-11-18); both null for any other event, and on entries
 * logged before they existed. [battleSummary] is a finished battle told in one place (playtest, 2026-11-20), on its
 * BATTLE_SUMMARY entry only.
 */
data class LogEntry(
	val turn: Int,
	val text: String,
	val trade: TradeRecord?,
	val notice: Notice?,
	val battleSummary: BattleSummary? = null
) {

	/** This entry as part of the pop-up moment [notice], unless it already is one of its own. */
	fun noticed(notice: Notice): LogEntry = if (this.notice == null) copy(notice = notice) else this

	companion object {
		/** An event that is neither a trade nor a pop-up moment. */
		fun event(turn: Int, text: String) = LogEntry(turn, text, null, null)

		/** A trade, or nothing traded when [trade] is null. */
		fun trade(turn: Int, text: String, trade: TradeRecord?) = LogEntry(turn, text, trade, null)

		/** An interception or battle moment, [notice]. */
		fun notice(turn: Int, text: String, notice: Notice) = LogEntry(turn, text, null, notice)

		/** The [summary] of a battle that just finished, its moment [notice]. */
		fun battleSummary(turn: Int, text: String, notice: Notice, summary: BattleSummary) = LogEntry(turn, text, null, notice, summary)
	}
}
