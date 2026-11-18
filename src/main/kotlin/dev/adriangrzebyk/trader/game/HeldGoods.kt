package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods

/**
 * [units] of [goods] in one of a player's ships or warehouses, with [price], today's price of that good where it is, or
 * null when that system doesn't trade it (creator, 2026-11-18: the Assets tab shows where a player's money sits).
 */
data class HeldGoods(val goods: Goods, val units: Int, val price: Int?) {

	/** What it is worth at today's price where it is: 0 where it isn't traded. */
	fun value(): Int = units * (price ?: 0)
}
