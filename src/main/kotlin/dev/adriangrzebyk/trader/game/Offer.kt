package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A trade offer [playerEmail] placed during Bidding: to buy or sell [goods] at [system] for [price] HT each.
 * Secret (R17): only its player sees it until Trading resolves it.
 */
data class Offer(val playerEmail: String, val system: StarSystem, val goods: Goods, val type: OfferType, val price: Int) {

	companion object {
		/** R03: an offer's price is a whole number of HT from 1 to 20. */
		const val MIN_PRICE = 1
		const val MAX_PRICE = 20
	}
}
