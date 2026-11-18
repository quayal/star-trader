package dev.adriangrzebyk.trader.game

/**
 * One public event of the Game log (s04, D07), in the [turn] it happened in, so the log can be read by turn (S06-1).
 * Public by definition: it never carries an offer, a bid while Bidding is open, money or anything else confidential (R17,
 * R29, S06-3). [trade] is the trade the line tells of, for a player's trade history (creator, 2026-11-18); null for any
 * other event, and on entries logged before it existed.
 */
data class LogEntry(val turn: Int, val text: String, val trade: TradeRecord?) {

	companion object {
		/** An event that isn't a trade. */
		fun event(turn: Int, text: String) = LogEntry(turn, text, null)
	}
}
