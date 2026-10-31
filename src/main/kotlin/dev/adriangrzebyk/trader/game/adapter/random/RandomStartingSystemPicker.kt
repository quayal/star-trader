package dev.adriangrzebyk.trader.game.adapter.random

import dev.adriangrzebyk.trader.game.StartingSystemPicker
import dev.adriangrzebyk.trader.trading.StarSystem
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class RandomStartingSystemPicker(private val random: Random = Random.Default) : StartingSystemPicker {

	override fun pickTwoDifferentSystems(): Pair<StarSystem, StarSystem> {
		val (first, second) = StarSystem.entries.shuffled(random).take(2)
		return first to second
	}
}
