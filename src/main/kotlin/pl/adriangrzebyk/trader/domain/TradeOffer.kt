package pl.adriangrzebyk.trader.domain

data class TradeOffer(
	val player: Player? = null,
	val system: StarSystem? = null,
	val goods: Goods? = null,
	val offerType: OfferType? = null,
	val price: Int = 0,
	val initiative: Int = 0,
) {
	val isBuy: Boolean
		get() = offerType == OfferType.BUY
}
