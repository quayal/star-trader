package dev.adriangrzebyk.trader.game.loan

/** R90, R91: how long a loan runs, [turns], and what its interest divides (amount − reputation) by, [divisor]. */
enum class LoanTerm(val turns: Int, val divisor: Int) {
	FOUR(4, 20),
	EIGHT(8, 10);

	companion object {
		/** The term of [turns] turns, or null when no loan runs that long (R90: 4 or 8). */
		fun ofTurns(turns: Int?): LoanTerm? = entries.firstOrNull { it.turns == turns }
	}
}
