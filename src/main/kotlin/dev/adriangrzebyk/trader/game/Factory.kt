package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's factory for [goods] at [system] (R46): at most one per player per good per system, producing [size] units
 * a turn. [goods] is one the system can produce ([StarSystem.producibleGoods]). Public, like the board (R50).
 */
data class Factory(val system: StarSystem, val goods: Goods, val size: Int)
