package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/** [offer] traded [units] at its own price in a Trading resolution (R34). Public, like the resolution. */
data class Trade(val offer: Offer, val units: Int) {

	/** The counter after this trade, from [counter] before it: each unit moves it one step, down for a buy, up for a sell (R34). */
	fun counterAfter(counter: Int): Int = when (offer.type) {
		OfferType.BUY -> counter - units
		OfferType.SELL -> counter + units
	}

	/** For the public log (E05-S04-6): "bought 4 u at 11 HT each". */
	fun terms(): String {
		val verb = when (offer.type) {
			OfferType.BUY -> "bought"
			OfferType.SELL -> "sold"
		}
		return "$verb $units u at ${offer.price} HT each"
	}
}
