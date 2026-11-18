package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.game.HeldGoods
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.Warehouse

/**
 * The Assets tab (creator, 2026-11-18): where the viewer's money sits, for them only (R29); their cash is `you.money`. [shipsValue] is what their
 * ships would fetch sold (R61), [goodsValue] what the goods in their ships and warehouses are worth at today's prices where
 * each is, and [factoryOutputValue] what one turn's output of their factories is worth at today's prices.
 */
data class AssetsResponseDto(
	val shipsValue: Int,
	val goodsValue: Int,
	val factoryOutputValue: Int,
	val ships: List<ShipAssetResponseDto>,
	val warehouses: List<WarehouseAssetResponseDto>,
	val factories: List<FactoryAssetResponseDto>
) {

	constructor(player: Player, prices: MarketPrices) : this(
		player.shipsSaleValue(),
		player.goodsValue(prices),
		player.factoryOutputValue(prices),
		player.ships.map { ShipAssetResponseDto(it, prices) },
		player.warehouses.map { WarehouseAssetResponseDto(it, prices) },
		player.factories.map { FactoryAssetResponseDto(it, prices) }
	)
}

/** One of the viewer's ships as an asset: where it is, what it would fetch sold, and its goods at today's prices there. */
data class ShipAssetResponseDto(
	val name: String,
	val system: String,
	val systemName: String,
	val location: String,
	val saleValue: Int,
	val goods: List<HeldGoodsResponseDto>,
	val goodsValue: Int
) {

	constructor(ship: Ship, prices: MarketPrices) : this(
		ship.name,
		ship.system.name,
		ship.system.displayName,
		ship.location.name,
		ship.saleValue(),
		ship.heldGoods(prices).map { HeldGoodsResponseDto(it) },
		ship.heldGoodsValue(prices)
	)
}

/** One of the viewer's warehouses as an asset: its blocks and its goods at today's prices there. */
data class WarehouseAssetResponseDto(
	val system: String,
	val systemName: String,
	val blocks: Int,
	val goods: List<HeldGoodsResponseDto>,
	val goodsValue: Int
) {

	constructor(warehouse: Warehouse, prices: MarketPrices) : this(
		warehouse.system.name,
		warehouse.system.displayName,
		warehouse.blocks,
		warehouse.heldGoods(prices).map { HeldGoodsResponseDto(it) },
		warehouse.heldGoodsValue(prices)
	)
}

/** One of the viewer's factories as an asset: units a turn, today's [price] of its good there (null where not traded), and a turn's output at it. */
data class FactoryAssetResponseDto(
	val system: String,
	val systemName: String,
	val goods: String,
	val size: Int,
	val price: Int?,
	val outputValue: Int
) {

	constructor(factory: Factory, prices: MarketPrices) : this(
		factory.system.name,
		factory.system.displayName,
		factory.goods.name,
		factory.size,
		factory.outputPrice(prices),
		factory.outputValue(prices)
	)
}

/** Units of one good held, today's [price] where they are (null where it isn't traded) and their [value] at it. */
data class HeldGoodsResponseDto(val goods: String, val units: Int, val price: Int?, val value: Int) {

	constructor(held: HeldGoods) : this(held.goods.name, held.units, held.price, held.value())
}
