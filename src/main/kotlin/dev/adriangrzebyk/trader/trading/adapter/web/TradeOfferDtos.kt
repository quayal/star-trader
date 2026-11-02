package dev.adriangrzebyk.trader.trading.adapter.web

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType

data class GoodsMarket(val goods: Goods, val price: Int, val indicator: Int)

data class StarSystemOption(val name: String, val displayName: String, val goods: List<Goods>, val market: List<GoodsMarket>)

data class TradeOfferOptions(val starSystems: List<StarSystemOption>, val offerTypes: List<OfferType>, val minPrice: Int, val maxPrice: Int)
