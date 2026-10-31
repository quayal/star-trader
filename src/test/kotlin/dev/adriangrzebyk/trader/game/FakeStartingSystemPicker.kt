package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem

/** Hands out the given pairs in order, one per player. */
class FakeStartingSystemPicker(vararg pairs: Pair<StarSystem, StarSystem>) : StartingSystemPicker {

	private val remaining = ArrayDeque(pairs.toList())

	override fun pickTwoDifferentSystems(): Pair<StarSystem, StarSystem> = remaining.removeFirst()
}
