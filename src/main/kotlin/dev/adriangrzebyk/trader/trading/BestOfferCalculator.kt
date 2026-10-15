package dev.adriangrzebyk.trader.trading

class BestOfferCalculator {

	fun getWinningOffer(system: StarSystem, goods: Goods, offers: Set<TradeOffer>, diceRoll: Int): TradeOffer {
		val indicator = diceRoll + system.getIndicator(goods)
		val relevantOffers = offers.filter { it.system == system && it.goods == goods }

		return if (indicator >= 0) {
			relevantOffers.filter { it.offerType == OfferType.BUY }.reduce(::betterBuy)
		} else {
			relevantOffers.filter { it.offerType == OfferType.SELL }.reduce(::betterSell)
		}
	}

	private fun betterBuy(offer: TradeOffer, other: TradeOffer): TradeOffer =
		if (offer.price != other.price) {
			if (offer.price < other.price) other else offer
		} else {
			if (offer.initiative < other.initiative) offer else other
		}

	private fun betterSell(offer: TradeOffer, other: TradeOffer): TradeOffer =
		if (offer.price != other.price) {
			if (offer.price < other.price) offer else other
		} else {
			if (offer.initiative < other.initiative) offer else other
		}
}
