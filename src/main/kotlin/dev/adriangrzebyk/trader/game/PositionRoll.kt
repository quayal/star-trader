package dev.adriangrzebyk.trader.game

/**
 * The 2d6 roll after a trade of at least [MIN_UNITS] units (R43, E05-S06-2), public like the trade (S06-4): [roll], and
 * [promotedTo], the tier its trader now holds for that good at that system, or null when the roll was above the units
 * traded. [demoted] is the Dictator it replaced, who is Wholesaler now (S06-3).
 */
data class PositionRoll(val roll: Int, val promotedTo: MarketTier?, val demoted: List<String>) {

	/**
	 * For the public log and s03 (S06-4), after "Vega Lines traded 7 u — ": "rolled 5: now Wholesaler of ISO at Mu
	 * Herculis", or "rolled 9: no change". The Dictator's demotion is the caller's, which knows the names.
	 */
	fun outcome(good: MarketGood): String {
		val change = promotedTo?.let { "now ${it.displayName} of ${good.goods.name} at ${good.system.displayName}" }
		return "rolled $roll: ${change ?: "no change"}"
	}

	companion object {
		/** R43: only a trade of at least 6 units of a good rolls for a position. */
		const val MIN_UNITS = 6
	}
}
