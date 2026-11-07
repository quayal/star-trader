package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import dev.adriangrzebyk.trader.trading.SupplyDemand

/**
 * A trade offer [playerEmail] placed during Bidding: to buy or sell [goods] at [system] for [price] HT each.
 * Secret (R17): only its player sees it until Trading resolves it.
 */
data class Offer(val playerEmail: String, val system: StarSystem, val goods: Goods, val type: OfferType, val price: Int) {

	/** Whether this offer is for [good]: the same good at the same system. */
	fun isFor(good: MarketGood): Boolean = system == good.system && goods == good.goods

	/**
	 * The most units this offer allows at [counter] (R34): for a buy, the counter less the buy target of its price's
	 * distance from [startPrice]; for a sell, the sell target less the counter (R13, R15 saturation). The start price is
	 * used for the whole resolution. Zero or less means it can't trade.
	 */
	fun maxUnits(counter: Int, startPrice: Int): Int = when (type) {
		OfferType.BUY -> counter - SupplyDemand.buyTargetFor(price - startPrice).indicator
		OfferType.SELL -> SupplyDemand.sellTargetFor(price - startPrice).indicator - counter
	}

	/** For the public log once it's disclosed (E05-S02): "buy at 11 HT". */
	fun terms(): String = "${type.name.lowercase()} at $price HT"

	companion object {
		/** R03: an offer's price is a whole number of HT from 1 to 20. */
		const val MIN_PRICE = 1
		const val MAX_PRICE = 20

		/** A new offer, once checked that [goods] is traded at [system] and [price] is a whole number of HT from 1 to 20 (R03). */
		fun place(playerEmail: String, system: StarSystem, goods: Goods, type: OfferType, price: Int?): Offer {
			if (goods !in system.goods) {
				throw InvalidOfferException("${goods.name} isn't traded at ${system.displayName}.")
			}
			if (price == null || price !in MIN_PRICE..MAX_PRICE) {
				throw InvalidOfferException("Price must be a whole number from $MIN_PRICE to $MAX_PRICE.")
			}
			return Offer(playerEmail, system, goods, type, price)
		}
	}
}
