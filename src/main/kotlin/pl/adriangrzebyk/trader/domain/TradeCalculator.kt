package pl.adriangrzebyk.trader.domain

class TradeCalculator(
	private val system: StarSystem,
	private val indicators: Map<Goods, Int>,
	private val prices: Map<Goods, Int>,
) {

	internal fun getTheBestOffer(goods: Goods, offers: Set<TradeOffer>, diceRoll: Int): TradeOffer? {
		val indicator = diceRoll + indicators.getValue(goods)

		if (indicator < 0) {
			return null
		}

		val offersForGoods = offers.filter { it.goods == goods && it.system == system }

		return offersForGoods
			.filter { it.isBuy }
			.reduceOrNull(::getBetterBuy)
			?: TradeOffer()
	}

	internal fun getBetterBuy(offer: TradeOffer, other: TradeOffer): TradeOffer {
		if (offer.price != other.price) {
			return if (offer.price < other.price) other else offer
		}
		return if (offer.initiative < other.initiative) offer else other
	}
}
