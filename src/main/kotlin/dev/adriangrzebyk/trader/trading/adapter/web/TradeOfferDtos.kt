package dev.adriangrzebyk.trader.trading.adapter.web

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import dev.adriangrzebyk.trader.trading.TradeOffer

data class TradeOfferRequest(
	val playerName: String? = null,
	val system: StarSystem? = null,
	val goods: Goods? = null,
	val offerType: OfferType? = null,
	val price: Int? = null,
	val turn: Int = 1,
)

data class TradeOfferView(
	val turn: Int,
	val initiative: Int,
	val playerName: String,
	val system: StarSystem,
	val systemDisplayName: String,
	val goods: Goods,
	val offerType: OfferType,
	val price: Int,
) {
	companion object {
		fun from(offer: TradeOffer): TradeOfferView = TradeOfferView(
			turn = offer.turn,
			initiative = offer.initiative,
			playerName = offer.player?.name.orEmpty(),
			system = requireNotNull(offer.system),
			systemDisplayName = offer.system.displayName,
			goods = requireNotNull(offer.goods),
			offerType = requireNotNull(offer.offerType),
			price = offer.price,
		)
	}
}

data class TransactionView(val offer: TradeOfferView, val amount: Int)

data class GoodsMarket(val goods: Goods, val price: Int, val indicator: Int)

data class StarSystemOption(val name: String, val displayName: String, val goods: List<Goods>, val market: List<GoodsMarket>)

data class TradeOfferOptions(
	val starSystems: List<StarSystemOption>,
	val offerTypes: List<OfferType>,
	val minPrice: Int,
	val maxPrice: Int,
)
