package dev.adriangrzebyk.trader.game

/**
 * A player's reputation (R85, E13): a whole number from [MIN] to [MAX], [START] for everyone at the start, public. Every
 * change is clamped to that range (R86). It never decides the winner (R39), but at [BONUS_FROM] or more it pays a money
 * bonus at the end of each turn (R88).
 */
data class Reputation(val value: Int) {

	init {
		require(value in MIN..MAX) { "Reputation runs from $MIN to $MAX." }
	}

	/** This reputation moved by [delta], clamped to [MIN]..[MAX] (R86). */
	fun changedBy(delta: Int): Reputation = Reputation((value + delta).coerceIn(MIN, MAX))

	/**
	 * What the end of a turn adds (R86): [RECOVERY] between 1 and [RECOVERY_UP_TO], nothing otherwise. A player at 0 doesn't
	 * recover; only buying raises them (creator, 2026-11-18).
	 */
	fun recovery(): Int = if (value in 1..RECOVERY_UP_TO) RECOVERY else 0

	/** The money bonus this reputation pays at the end of a turn (R88): the creator's table from [BONUS_FROM] up, 0 below. */
	fun bonus(): Int = BONUS[value] ?: 0

	/** Whether this reputation is low enough for the Control phase to examine it (R129): [CONTROL_UP_TO] or below. */
	fun isUnderControl(): Boolean = value <= CONTROL_UP_TO

	/** Whether a d6 [roll] fails the first test of a reputation control (R129): higher than the reputation. */
	fun failsControl(roll: Int): Boolean = roll > value

	/** Whether this reputation is at [MAX], where buying more is refused (R87). */
	fun isMax(): Boolean = value == MAX

	companion object {
		const val MIN = 0
		const val MAX = 40

		/** R85: every player starts here; also what a saved game from before reputation loads as. */
		const val START = 20

		/** R86: the end-of-turn recovery, for a reputation from 1 up to [RECOVERY_UP_TO]. */
		const val RECOVERY = 3
		const val RECOVERY_UP_TO = 17

		/** R129: a reputation up to here is examined in the Control phase. */
		const val CONTROL_UP_TO = 5

		/** R134: what buying with a d6 [roll] adds: the roll, but nothing for a 6. */
		fun growthFromBuying(roll: Int): Int = if (roll == BUY_NO_GROWTH_ROLL) 0 else roll

		/** R134: the roll that buys no growth. */
		const val BUY_NO_GROWTH_ROLL = 6

		/** R87: what buying 1d6 of reputation costs, once per Investment. */
		const val BUY_COST = 20

		/** R88: the lowest reputation that pays a bonus. */
		const val BONUS_FROM = 25

		/** R88: the creator's bonus table, reputation → HT paid at the end of each turn; public for the Guide. */
		val BONUS = mapOf(
			25 to 5,
			26 to 5,
			27 to 10,
			28 to 10,
			29 to 15,
			30 to 20,
			31 to 20,
			32 to 20,
			33 to 20,
			34 to 25,
			35 to 25,
			36 to 30,
			37 to 30,
			38 to 30,
			39 to 35,
			40 to 40
		)

		val INITIAL = Reputation(START)
	}
}
