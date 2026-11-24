package dev.adriangrzebyk.trader.game.control

/**
 * The second roll of a reputation control (R129): a d6 [roll] less [reduction], the political connections of the player
 * controlled and of the one backing them (R129), read on the creator's table.
 */
data class ControlVerdict(val roll: Int, val reduction: Int) {

	fun value(): Int = roll - reduction

	/** The creator's table: 1 or below innocent, 2 a fine of 100 HT, 3 a fine of 400 HT, 4 taken over, 5 or more closed. */
	fun result(): ControlResult = when {
		value() <= 1 -> ControlResult.INNOCENT
		value() == 2 -> ControlResult.SMALL_FINE
		value() == 3 -> ControlResult.LARGE_FINE
		value() == 4 -> ControlResult.TAKEN_OVER
		else -> ControlResult.CLOSED
	}

	/** For the log: "5", or "6 − 1 = 5" when connections lowered it. */
	fun text(): String = if (reduction > 0) "$roll − $reduction = ${value()}" else "$roll"
}
