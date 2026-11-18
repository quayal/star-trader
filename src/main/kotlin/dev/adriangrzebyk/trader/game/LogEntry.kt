package dev.adriangrzebyk.trader.game

/**
 * One public event of the Game log (s04, D07), in the [turn] it happened in, so the log can be read by turn (S06-1).
 * Public by definition: it never carries an offer, a bid while Bidding is open, money or anything else confidential (R17,
 * R29, S06-3). [trade] is the trade the line tells of, for a player's trade history, and [notice] the interception or
 * battle moment it tells of, for the pop-up cards (creator, 2026-11-18); both null for any other event, and on entries
 * logged before they existed.
 */
data class LogEntry(val turn: Int, val text: String, val trade: TradeRecord?, val notice: Notice?) {

	companion object {
		/** An event that is neither a trade nor a pop-up moment. */
		fun event(turn: Int, text: String) = LogEntry(turn, text, null, null)

		/** A trade, or nothing traded when [trade] is null. */
		fun trade(turn: Int, text: String, trade: TradeRecord?) = LogEntry(turn, text, trade, null)

		/** An interception or battle moment, [notice]. */
		fun notice(turn: Int, text: String, notice: Notice) = LogEntry(turn, text, null, notice)
	}
}
