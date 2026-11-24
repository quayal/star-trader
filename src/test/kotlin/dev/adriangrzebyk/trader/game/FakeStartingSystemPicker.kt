package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem

/** Hands out the given pairs in order, one per player: where [StartingShips] puts a test game's two ships each. */
class FakeStartingSystemPicker(vararg pairs: Pair<StarSystem, StarSystem>) {

	private val remaining = ArrayDeque(pairs.toList())

	fun pickTwoDifferentSystems(): Pair<StarSystem, StarSystem> = remaining.removeFirst()
}
