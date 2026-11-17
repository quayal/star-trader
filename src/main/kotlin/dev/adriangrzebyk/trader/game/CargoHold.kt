package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods

/** Something that holds goods and can take them in or give them up: a [Ship] or a [Warehouse] (R42, R45, R58). */
sealed interface CargoHold {

	/** How it is named in a message to its owner: "Wayfarer", "Your warehouse at Tau Ceti". */
	fun cargoName(): String

	/** How many units of [goods] it holds. */
	fun holding(goods: Goods): Int

	/** How many of [units] of [goods] fit (R42): POLY and ISO up to the free cargo space, CHIPS and FOOD all. */
	fun unitsThatFit(goods: Goods, units: Int): Int

	/** It after taking in [units] of [goods], as far as they fit. */
	fun load(goods: Goods, units: Int): CargoHold

	/** It after giving up [units] of [goods], refused when it holds fewer. */
	fun unload(goods: Goods, units: Int): CargoHold
}
