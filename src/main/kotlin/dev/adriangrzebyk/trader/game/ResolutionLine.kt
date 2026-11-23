package dev.adriangrzebyk.trader.game

/**
 * One public log line of what a resolution did ([Resolution.logSince]): its [text], the [trade] it tells of for a player's
 * trade history, and [rise] when it is a position roll that moved someone up a tier (R43), after which the game logs
 * the reputation it moved (R86), and [notice] when the roll went against someone (R124).
 */
data class ResolutionLine(val text: String, val trade: TradeRecord?, val rise: MarketRise?, val notice: Notice? = null) {

	companion object {
		/** A line that tells of no trade and no rise. */
		fun event(text: String) = ResolutionLine(text, null, null)
	}
}

/** A position roll's rise (R43, E05-S06): [riser] went up a tier, and [demoted] are the Dictators it replaced (S06-3). */
data class MarketRise(val riser: String, val demoted: List<String>)
