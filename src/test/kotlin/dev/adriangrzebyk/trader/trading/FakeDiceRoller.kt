package dev.adriangrzebyk.trader.trading

class FakeDiceRoller(private val roll: Int) : DiceRoller {

	override fun roll1D(): Int = roll

	override fun roll2D(): Int = roll

	override fun roll3D(): Int = roll
}
