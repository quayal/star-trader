package dev.adriangrzebyk.trader.game

/**
 * Hands out the given d2 and 2d6 rolls in order, each from its own queue; running out fails the test, so every roll a
 * rule takes is spelled out.
 */
class FakeDice(d2Rolls: List<Int>, twoD6Rolls: List<Int>) : Dice {

	/** Only d2 rolls: for rules that never roll 2d6. */
	constructor(vararg d2Rolls: Int) : this(d2Rolls.toList(), emptyList())

	private val remainingD2 = ArrayDeque(d2Rolls)
	private val remainingTwoD6 = ArrayDeque(twoD6Rolls)

	override fun d2(): Int = remainingD2.removeFirstOrNull() ?: error("FakeDice ran out of d2 rolls")

	override fun twoD6(): Int = remainingTwoD6.removeFirstOrNull() ?: error("FakeDice ran out of 2d6 rolls")

	companion object {
		/** Only 2d6 rolls: for Trading, where the counter rolls (R34). */
		fun twoD6(vararg rolls: Int) = FakeDice(emptyList(), rolls.toList())
	}
}
