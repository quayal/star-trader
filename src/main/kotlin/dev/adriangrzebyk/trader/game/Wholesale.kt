package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/**
 * What [playerEmail], asked in a resolution's wholesale step (R37), answered: Buy or Sell ([type]) of [units] at [price]
 * HT each, the good's new price; or a pass, with no type and 0 units. Public and logged (E05-S05-5); it doesn't move the
 * price.
 */
data class Wholesale(val playerEmail: String, val type: OfferType?, val units: Int, val price: Int) {

	/**
	 * For the public log and s03 (S05-5), with the total like every trade line (E05-S04-6): "bought 3 u at 10 HT each,
	 * paid 30 HT", "sold 2 u at 10 HT each, received 20 HT", or "passed on wholesale" (a Dictator's pass before the roll
	 * is just "passed").
	 */
	fun terms(): String = when (type) {
		null -> "passed on wholesale"
		OfferType.BUY -> "bought $units u at $price HT each, paid ${units * price} HT"
		OfferType.SELL -> "sold $units u at $price HT each, received ${units * price} HT"
	}

	companion object {
		/** R37: a wholesale is at most 5 units. */
		const val MAX_UNITS = 5
	}
}
