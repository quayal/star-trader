package dev.adriangrzebyk.trader.game.control

/**
 * A ship's chance of being caught when police controls Space (R128), before it rolls: of the 36 2d6 outcomes with its
 * [bonus] against [police], how many a [PoliceRoll] doesn't evade, as a whole percentage. What the Jumps screen shows for
 * staying in Space at each destination.
 */
data class PoliceOdds(val bonus: Int, val police: Int) {

	fun caughtPercent(): Int {
		val caught = (1..6).sumOf { a -> (1..6).count { b -> !PoliceRoll(a + b, bonus, police).evaded() } }
		return Math.round(caught * 100.0 / OUTCOMES).toInt()
	}

	private companion object {
		const val OUTCOMES = 36
	}
}
