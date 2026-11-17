package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods

/**
 * What happens to [ship]'s cargo when it's sold at a system where its owner has [warehouse] (R66): each good, in goods
 * order, goes into the warehouse as far as it fits (POLY and ISO take one cargo space a unit, CHIPS and FOOD none, R42);
 * the rest is lost. Nothing ever blocks the sale. `js/shipyard.js` tells the seller the same before and after.
 */
class ShipSale(private val ship: Ship, private val warehouse: Warehouse) {

	/** The warehouse once the ship's cargo went into it. */
	fun warehouseAfter(): Warehouse = Goods.entries.fold(warehouse) { current, goods -> current.load(goods, ship.holding(goods)) }
}
