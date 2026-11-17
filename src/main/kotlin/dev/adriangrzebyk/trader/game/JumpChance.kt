package dev.adriangrzebyk.trader.game

/**
 * A ship's chance to make one jump (R68): a 2d6 roll of [target] or less succeeds. The target is the route's base chance
 * plus the ship's crew and Safe jump modifiers, so it can go past 12, which always succeeds.
 */
data class JumpChance(val target: Int) {

	/** Whether [roll], a 2d6 roll, makes the jump. */
	fun succeeds(roll: Int): Boolean = roll <= target

	/** The chance of success in whole percent: P(2d6 ≤ [target]), rounded to the nearest. */
	fun percent(): Int {
		val ways = (1..6).sumOf { first -> (1..6).count { second -> first + second <= target } }
		return Math.round(ways * 100.0 / 36).toInt()
	}
}
