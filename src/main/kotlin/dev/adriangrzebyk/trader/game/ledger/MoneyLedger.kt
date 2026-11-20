package dev.adriangrzebyk.trader.game.ledger

/**
 * A player's own money history (R29, playtest 2026-11-20): every movement with its reason, private to them. [turn] is the turn
 * new movements are recorded in; only the newest [KEPT] movements are kept, so a long game's item stays small.
 */
data class MoneyLedger(val turn: Int, val movements: List<MoneyMovement>) {

	/** This ledger with [amount] HT recorded for [reason] in the current turn; a movement of nothing isn't one. */
	fun recorded(amount: Int, reason: String): MoneyLedger =
		if (amount == 0) this else copy(movements = (movements + MoneyMovement(turn, reason, amount)).takeLast(KEPT))

	/** This ledger recording from turn [number] on. */
	fun inTurn(number: Int): MoneyLedger = copy(turn = number)

	/** The kept movements, newest first. */
	fun newestFirst(): List<MoneyMovement> = movements.reversed()

	companion object {
		const val KEPT = 40

		/** A ledger at the start of a game: turn 1, nothing recorded. */
		val START = MoneyLedger(1, emptyList())
	}
}
