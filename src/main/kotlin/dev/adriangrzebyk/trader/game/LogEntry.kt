package dev.adriangrzebyk.trader.game

/**
 * One public event of the Game log (s04, D07), in the [turn] it happened in, so the log can be read by turn (S06-1).
 * Public by definition: it never carries an offer, a bid, money or anything else confidential (R17, R29, S06-3).
 */
data class LogEntry(val turn: Int, val text: String)
