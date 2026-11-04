package dev.adriangrzebyk.trader.game.adapter.random

import dev.adriangrzebyk.trader.game.Dice
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class RandomDice(private val random: Random = Random.Default) : Dice {

	override fun d2(): Int = random.nextInt(1, 3)
}
