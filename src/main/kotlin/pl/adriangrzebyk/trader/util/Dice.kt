package pl.adriangrzebyk.trader.util

import kotlin.random.Random

object Dice {

	fun roll1D(): Int = Random.nextInt(1, 7)

	fun roll2D(): Int = roll1D() + roll1D()

	fun roll3D(): Int = roll2D() + roll1D()
}
