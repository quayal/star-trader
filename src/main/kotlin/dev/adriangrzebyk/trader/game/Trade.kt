package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/**
 * What happened when the counter reached [offer] in a Trading resolution (R34): the market allowed up to [marketMax]
 * units, and its player traded [units] of them at the offer's own price, 0 when they chose to trade nothing (E05-S04).
 * Public, like the resolution; why a player traded less is their own business (R29). [positionRoll] is the roll a trade of
 * 6 or more units makes for its player's market position, and what came of it (R43, E05-S06); null when it made none.
 */
data class Trade(val offer: Offer, val marketMax: Int, val units: Int, val positionRoll: PositionRoll?) {

	/** The counter after this trade, from [counter] before it: each unit moves it one step, down for a buy, up for a sell (R34). */
	fun counterAfter(counter: Int): Int = when (offer.type) {
		OfferType.BUY -> counter - units
		OfferType.SELL -> counter + units
	}

	/** Whether any unit traded. */
	fun tradedAny(): Boolean = units > 0

	/**
	 * For the public log and s03 (E05-S04-6): what the market allowed and what took place. "could buy up to 12 u: bought
	 * 10 u at 13 HT each, paid 130 HT", "could sell up to 10 u: sold 4 u at 9 HT each, received 36 HT", or "could buy up
	 * to 3 u: bought nothing".
	 */
	fun terms(): String {
		val (verb, done, total) = when (offer.type) {
			OfferType.BUY -> Triple("buy", "bought", "paid")
			OfferType.SELL -> Triple("sell", "sold", "received")
		}
		val allowed = "could $verb up to $marketMax u"
		if (!tradedAny()) {
			return "$allowed: $done nothing"
		}
		return "$allowed: $done $units u at ${offer.price} HT each, $total ${units * offer.price} HT"
	}
}
