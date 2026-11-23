package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem

/** A player's [tier] for one good at one system, never across systems or goods (R28). Public to every player. */
data class MarketPosition(val system: StarSystem, val goods: Goods, val tier: MarketTier) {

	/** Whether this position is for [good] at its system. */
	fun isFor(good: MarketGood): Boolean = system == good.system && goods == good.goods

	/** This position one tier lower after a challenge (R126), or null for a Retailer, who loses it altogether. */
	fun lowered(): MarketPosition? = tier.previous()?.let { copy(tier = it) }

	/** For the log and messages: "Dictator of CHIPS at Mu Herculis". */
	fun text(): String = "${tier.displayName} of ${goods.name} at ${system.displayName}"
}
