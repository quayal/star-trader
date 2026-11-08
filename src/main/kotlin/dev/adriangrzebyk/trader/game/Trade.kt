package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/**
 * [offer] traded [units] at its own price in a Trading resolution (R34). Public, like the resolution, except
 * [shortfall]: when the counter reached the offer but its player's max was 0, it traded 0 units, and [shortfall] says
 * why, to that player only (E05-S04-5, R29).
 */
data class Trade(val offer: Offer, val units: Int, val shortfall: Shortfall? = null) {

	/** [offer] done with no units, its player's max being 0 (E05-S04-5), and why. */
	constructor(offer: Offer) : this(offer, 0, offer.shortfall())

	/** The counter after this trade, from [counter] before it: each unit moves it one step, down for a buy, up for a sell (R34). */
	fun counterAfter(counter: Int): Int = when (offer.type) {
		OfferType.BUY -> counter - units
		OfferType.SELL -> counter + units
	}

	/** Whether any unit traded: an offer whose player couldn't trade is done with none (E05-S04-5). */
	fun tradedAny(): Boolean = units > 0

	/** For the public log (E05-S04-6): "bought 4 u at 11 HT each", or "couldn't trade" with no reason (R29). */
	fun terms(): String {
		if (!tradedAny()) {
			return "couldn't trade"
		}
		val verb = when (offer.type) {
			OfferType.BUY -> "bought"
			OfferType.SELL -> "sold"
		}
		return "$verb $units u at ${offer.price} HT each"
	}
}
