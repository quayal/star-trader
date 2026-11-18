package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem

/**
 * [units] of [goods] in one of a player's holds at [system]: in their ship [ship], or in their warehouse there when
 * [ship] is null (creator, 2026-11-18: the Assets tab shows where each good is).
 */
data class GoodsLot(val goods: Goods, val units: Int, val system: StarSystem, val ship: String?)

/**
 * All of one [goods] a player holds, [lots] by where it is, valued at [averagePrice], the mean of today's prices across
 * the systems that trade it (creator, 2026-11-18): what it is worth wherever it ends up being sold.
 */
data class GoodsStock(val goods: Goods, val averagePrice: Int, val lots: List<GoodsLot>) {

	/** How many units of it the player holds, wherever they are. */
	fun units(): Int = lots.sumOf { it.units }

	/** What it is worth at [averagePrice]. */
	fun value(): Int = units() * averagePrice
}
