package dev.adriangrzebyk.trader.game.control

/**
 * A ship's roll when police controls Space (R128): a 2d6 [roll] plus its [bonus] (a working Battle comm module) against the
 * system's [police] this turn. Higher than police, the ship evaded control; equal or lower, it's caught. Public, like the
 * ship's position (R57).
 */
data class PoliceRoll(val roll: Int, val bonus: Int, val police: Int) {

	fun total(): Int = roll + bonus

	/** R128: above police the ship evaded the control. */
	fun evaded(): Boolean = total() > police

	/** For the log: "9 vs police 7", or "6 + 2 = 8 vs police 8" with a bonus. */
	fun text(): String = (if (bonus > 0) "$roll + $bonus = ${total()}" else "$roll") + " vs police $police"
}
