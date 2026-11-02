package dev.adriangrzebyk.trader.game

/** R28: the market-position tiers, lowest first. Retailer is an explicit tier a player must hold, not a default. */
enum class MarketTier {
	RETAILER,
	WHOLESALER,
	DICTATOR
}
