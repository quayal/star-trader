package dev.adriangrzebyk.trader.game

/**
 * Hands out the given 2d6, d6 and d3 rolls in order, each from its own queue; running out fails the test, so every roll a
 * rule takes is spelled out.
 */
class FakeDice(
	twoD6Rolls: List<Int>,
	d6Rolls: List<Int> = emptyList(),
	d3Rolls: List<Int> = emptyList(),
	oneOfRolls: List<Int> = emptyList()
) : Dice {

	/** Only 2d6 rolls: for closing Bidding, where each player rolls for initiative (R27), and Trading's counter (R34). */
	constructor(vararg twoD6Rolls: Int) : this(twoD6Rolls.toList())

	private val remainingTwoD6 = ArrayDeque(twoD6Rolls)
	private val remainingD6 = ArrayDeque(d6Rolls)
	private val remainingD3 = ArrayDeque(d3Rolls)
	private val remainingOneOf = ArrayDeque(oneOfRolls)

	override fun twoD6(): Int = remainingTwoD6.removeFirstOrNull() ?: error("FakeDice ran out of 2d6 rolls")

	override fun d6(): Int = remainingD6.removeFirstOrNull() ?: error("FakeDice ran out of d6 rolls")

	override fun d3(): Int = remainingD3.removeFirstOrNull() ?: error("FakeDice ran out of d3 rolls")

	override fun oneOf(faces: Int): Int = remainingOneOf.removeFirstOrNull() ?: error("FakeDice ran out of oneOf rolls")

	companion object {
		/** Only 2d6 rolls: for Trading, where the counter rolls (R34). */
		fun twoD6(vararg rolls: Int) = FakeDice(rolls.toList())

		/** Every 2d6 is [roll], however many are taken: 2 makes every jump succeed (R68), for tests about something else. */
		fun everyTwoD6(roll: Int): Dice = object : Dice {
			override fun twoD6(): Int = roll

			override fun d6(): Int = error("FakeDice.everyTwoD6 has no d6 rolls")

			override fun d3(): Int = error("FakeDice.everyTwoD6 has no d3 rolls")

			override fun oneOf(faces: Int): Int = error("FakeDice.everyTwoD6 has no oneOf rolls")
		}

		/** Every d3 is [roll], however many are taken: for starting a game (R82). */
		fun everyD3(roll: Int): Dice = object : Dice {
			override fun twoD6(): Int = error("FakeDice.everyD3 has no 2d6 rolls")

			override fun d6(): Int = error("FakeDice.everyD3 has no d6 rolls")

			override fun d3(): Int = roll

			override fun oneOf(faces: Int): Int = error("FakeDice.everyD3 has no oneOf rolls")
		}

		/** Only d3 rolls: for the starting connection points (R82), one per player in join order. */
		fun d3(vararg rolls: Int) = FakeDice(emptyList(), emptyList(), rolls.toList())

		/** Only d6 rolls: for interception (R70). */
		fun d6(vararg rolls: Int) = FakeDice(emptyList(), rolls.toList())

		/** Battle rolls, all d6 in the order taken: each salvo's roll, then its rocket check if it has one (R75). */
		fun battle(vararg d6: Int) = FakeDice(emptyList(), d6.toList())
	}
}

/** Dice whose every 2d6 is a 2, so every jump succeeds (R68): for tests about anything but the jump roll. */
val JUMPS_SUCCEED: Dice = FakeDice.everyTwoD6(2)

/**
 * Dice for starting a game in tests about anything else: every d3 a 3, so each player has 6 connection points to place
 * (R82), which [ConnectionsPlaced] places.
 */
val START_DICE: Dice = FakeDice.everyD3(3)
