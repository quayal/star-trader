package dev.adriangrzebyk.trader.game

/**
 * R28: the market-position tiers, lowest first, with the name the log and screens use. Retailer is an explicit tier a
 * player must hold, not a default.
 */
enum class MarketTier(val displayName: String) {
	RETAILER("Retailer"),
	WHOLESALER("Wholesaler"),
	DICTATOR("Dictator");

	/** The tier below this one (R126), or null for a Retailer, whose position is lost altogether. */
	fun previous(): MarketTier? = entries.getOrNull(ordinal - 1)

	/** The tier a position roll moves this one up to (R43), or null for a Dictator, who can't go higher (E05-S06-3). */
	fun next(): MarketTier? = entries.getOrNull(ordinal + 1)
}
