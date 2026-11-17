package dev.adriangrzebyk.trader.game

/** A salvo's weapons + 2d6 (R75), read against the battle table. */
@JvmInline
value class AttackTotal(val value: Int) {

	/** The hits this attack scores against a ship with [shields] 1–4 (R75, the creator's table with its two corrections). */
	fun hitsAgainst(shields: Int): Int {
		val row = ROWS.lastOrNull { value >= it.first } ?: return 0
		return row.second[shields.coerceIn(1, 4) - 1]
	}

	private companion object {
		/** Each row's lowest total and its hits against shields 1, 2, 3 and 4. */
		val ROWS = listOf(
			2 to listOf(0, 0, 0, 0),
			5 to listOf(1, 0, 0, 0),
			7 to listOf(1, 1, 0, 0),
			10 to listOf(2, 1, 1, 0),
			13 to listOf(2, 2, 1, 1),
			17 to listOf(3, 2, 2, 1),
			21 to listOf(4, 3, 2, 2),
			26 to listOf(5, 4, 3, 2),
			31 to listOf(6, 5, 4, 3)
		)
	}
}
