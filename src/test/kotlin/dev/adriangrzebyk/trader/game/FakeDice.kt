package dev.adriangrzebyk.trader.game

/** Hands out the given d2 rolls in order; running out fails the test, so every roll a rule takes is spelled out. */
class FakeDice(vararg rolls: Int) : Dice {

	private val remaining = ArrayDeque(rolls.toList())

	override fun d2(): Int = remaining.removeFirstOrNull() ?: error("FakeDice ran out of rolls")
}
