package dev.adriangrzebyk.trader.game.sabotage

/**
 * The creator's Sabotage table (R98, vault note "Operatives and sabotage"): a 2d6 + modifiers total read in the Effect
 * column for the effect roll and in the Reputation loss column for the reputation roll. A total below 2 reads as the
 * first row; 21 and more is the last.
 */
class SabotageTable {

	/** The effect of an effect roll totalling [total] (R99): hits, units missing or turns disabled. */
	fun effect(total: Int): Int = rowFor(total).effect

	/**
	 * The reputation the saboteur loses on a reputation roll totalling [total] (R100), or null on 21+, when the saboteur
	 * loses nothing and the innocent player they named loses [BLAME] instead.
	 */
	fun reputationLoss(total: Int): Int? = rowFor(total).reputationLoss

	private fun rowFor(total: Int): Row = ROWS.last { total >= it.from }

	private data class Row(val from: Int, val effect: Int, val reputationLoss: Int?)

	companion object {
		/** R100: what the innocent player named loses when the reputation roll is 21 or more. */
		const val BLAME = 8

		private val ROWS = listOf(
			Row(Int.MIN_VALUE, 0, 0),
			Row(7, 0, 10),
			Row(11, 1, 10),
			Row(13, 2, 6),
			Row(15, 3, 4),
			Row(17, 5, 0),
			Row(19, 7, 0),
			Row(21, 10, null)
		)
	}
}
