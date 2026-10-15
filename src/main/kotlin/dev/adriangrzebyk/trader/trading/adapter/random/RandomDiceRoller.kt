package dev.adriangrzebyk.trader.trading.adapter.random

import dev.adriangrzebyk.trader.trading.DiceRoller
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class RandomDiceRoller : DiceRoller {

	override fun roll1D(): Int = Random.nextInt(1, 7)

	override fun roll2D(): Int = roll1D() + roll1D()

	override fun roll3D(): Int = roll2D() + roll1D()
}
