package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem

/**
 * Where a player puts [units] of what their factory for [goods] at [system] made this turn (R47, E06-S03-2): into their
 * ship called [shipName] docked there, or, with a null [shipName], into their warehouse at that system.
 */
data class OutputPlacement(val system: StarSystem, val goods: Goods, val shipName: String?, val units: Int)
