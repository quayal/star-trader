package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.game.GoodsLot
import dev.adriangrzebyk.trader.game.GoodsStock
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Warehouse

/**
 * The Assets tab (creator, 2026-11-18): where the viewer's money sits, for them only (R29); their cash is `you.money`.
 * [shipsValue] is what their ships would fetch sold (R61); [goods] is every good they hold, by type and where it is, and
 * [goodsValue] what it is worth at each good's mean price across the systems that trade it today; [factoryOutputValue] is
 * one turn's output of their factories at those mean prices. Their ships themselves are on the Fleet tab.
 */
data class AssetsResponseDto(
	val shipsValue: Int,
	val goodsValue: Int,
	val factoryOutputValue: Int,
	val goods: List<GoodsStockResponseDto>,
	val warehouses: List<WarehouseAssetResponseDto>,
	val factories: List<FactoryAssetResponseDto>
) {

	constructor(player: Player, prices: MarketPrices) : this(
		player.shipsSaleValue(),
		player.goodsValue(prices),
		player.factoryOutputValue(prices),
		player.goodsStocks(prices).map { GoodsStockResponseDto(it) },
		player.warehouses.map { WarehouseAssetResponseDto(it) },
		player.factories.map { FactoryAssetResponseDto(it, prices) }
	)
}

/** All of one good the viewer holds: how many units, the mean price it is valued at, its value, and where the units are. */
data class GoodsStockResponseDto(
	val goods: String,
	val units: Int,
	val averagePrice: Int,
	val value: Int,
	val lots: List<GoodsLotResponseDto>
) {

	constructor(stock: GoodsStock) : this(
		stock.goods.name,
		stock.units(),
		stock.averagePrice,
		stock.value(),
		stock.lots.map { GoodsLotResponseDto(it) }
	)
}

/** Units of a good in one hold: the ship named [ship], or the warehouse at [system] when [ship] is null. */
data class GoodsLotResponseDto(val units: Int, val system: String, val systemName: String, val ship: String?) {

	constructor(lot: GoodsLot) : this(lot.units, lot.system.name, lot.system.displayName, lot.ship)
}

/** One of the viewer's warehouses as an asset: where it is and its blocks; its goods are under [AssetsResponseDto.goods]. */
data class WarehouseAssetResponseDto(val system: String, val systemName: String, val blocks: Int) {

	constructor(warehouse: Warehouse) : this(warehouse.system.name, warehouse.system.displayName, warehouse.blocks)
}

/** One of the viewer's factories as an asset: units a turn, its good's mean [averagePrice] today, and a turn's output at it. */
data class FactoryAssetResponseDto(
	val system: String,
	val systemName: String,
	val goods: String,
	val size: Int,
	val averagePrice: Int,
	val outputValue: Int
) {

	constructor(factory: Factory, prices: MarketPrices) : this(
		factory.system.name,
		factory.system.displayName,
		factory.goods.name,
		factory.size,
		prices.averagePrice(factory.goods),
		factory.outputValue(prices)
	)
}
