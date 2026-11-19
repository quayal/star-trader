package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType

data class GoodsMarket(val goods: Goods, val price: Int, val indicator: Int)

data class StarSystemOption(val name: String, val displayName: String, val goods: List<Goods>, val market: List<GoodsMarket>)

data class TradeOfferOptions(val starSystems: List<StarSystemOption>, val offerTypes: List<OfferType>, val minPrice: Int, val maxPrice: Int)
