package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

/** A player's [tier] for one good at one system, never across systems or goods (R28). Public to every player. */
data class MarketPosition(val system: StarSystem, val goods: Goods, val tier: MarketTier)
