package dev.adriangrzebyk.trader.trading

interface DiceRoller {

	fun roll1D(): Int

	fun roll2D(): Int

	fun roll3D(): Int
}
