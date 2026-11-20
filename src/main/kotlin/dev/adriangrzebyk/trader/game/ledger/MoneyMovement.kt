package dev.adriangrzebyk.trader.game.ledger

/** One change to a player's money in turn [turn], for [reason]: [amount] HT, positive when paid to them (R29, playtest 2026-11-20). */
data class MoneyMovement(val turn: Int, val reason: String, val amount: Int) {

	/** Whether this movement paid the player (shown in green), not took from them (red). */
	fun isCredit(): Boolean = amount > 0
}
