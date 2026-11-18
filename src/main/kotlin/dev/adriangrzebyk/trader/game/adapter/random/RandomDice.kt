package dev.adriangrzebyk.trader.game.adapter.random

import dev.adriangrzebyk.trader.game.Dice
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class RandomDice(private val random: Random = Random.Default) : Dice {

	override fun d2(): Int = random.nextInt(1, 3)

	override fun twoD6(): Int = d6() + d6()

	override fun d6(): Int = random.nextInt(1, 7)

	override fun d3(): Int = random.nextInt(1, 4)
}
