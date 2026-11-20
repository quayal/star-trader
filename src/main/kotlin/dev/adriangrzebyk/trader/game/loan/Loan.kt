package dev.adriangrzebyk.trader.game.loan

/**
 * A player's open loan (R90–R95, D22): [amount] is what they still owe, [interest] what each turn of the [term] costs,
 * fixed when it was taken in turn [takenInTurn] and never recalculated (R91). Private to the borrower, like money (R29).
 */
data class Loan(val amount: Int, val interest: Int, val takenInTurn: Int, val term: LoanTerm) {

	/** Whether interest falls due in turn [turn]'s Investment (R92): every turn of the term, from the one after it was taken. */
	fun isInterestDue(turn: Int): Boolean = turn in takenInTurn + 1..lastTurn()

	/** Whether the loan is collected when turn [turn]'s Investment closes (R94): the last turn of its term. */
	fun fallsDueIn(turn: Int): Boolean = turn >= lastTurn()

	/** How many interest payments are left from turn [turn] on, this one included when it's due. */
	fun turnsLeft(turn: Int): Int = (lastTurn() - maxOf(turn, takenInTurn + 1) + 1).coerceAtLeast(0)

	/** The loan after a turn's interest went unpaid (R92): it's added to what is owed, and the interest stays the same. */
	fun withInterestAdded(): Loan = copy(amount = amount + interest)

	/** What repaying it all costs (R93): what is owed, plus this turn's interest when it's due and unpaid. */
	fun repayment(interestUnpaid: Boolean): Int = amount + if (interestUnpaid) interest else 0

	private fun lastTurn(): Int = takenInTurn + term.turns

	companion object {
		/** R91: interest is at least 1 HT a turn (creator, 2026-11-20). */
		const val MIN_INTEREST = 1

		/** R91: ⌊(amount − reputation) / 20⌋ a turn for 4 turns, ⌊… / 10⌋ for 8, and at least [MIN_INTEREST]. */
		fun interestFor(amount: Int, term: LoanTerm, reputation: Int): Int = maxOf(MIN_INTEREST, Math.floorDiv(amount - reputation, term.divisor))

		/** A loan of [amount] HT over [term], taken in turn [turn] at [reputation] (R90, R91). */
		fun take(amount: Int, term: LoanTerm, reputation: Int, turn: Int): Loan = Loan(amount, interestFor(amount, term, reputation), turn, term)
	}
}
