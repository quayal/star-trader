package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem

/** Where a player's two ships start (R40): two different systems, chosen at random in play. */
interface StartingSystemPicker {

	fun pickTwoDifferentSystems(): Pair<StarSystem, StarSystem>
}
