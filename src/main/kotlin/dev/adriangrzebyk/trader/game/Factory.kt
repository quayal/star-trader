package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem

/**
 * A player's factory for [goods] at [system] (R46): at most one per player per good per system, producing [size] units
 * a turn. [goods] is one the system can produce ([StarSystem.producibleGoods]). Public, like the board (R50).
 */
data class Factory(val system: StarSystem, val goods: Goods, val size: Int) {

	/** Whether this is the factory for [goods] at [system]. */
	fun isFor(system: StarSystem, goods: Goods): Boolean = this.system == system && this.goods == goods

	/** What one turn's output is worth at its good's mean price across the systems that trade it today ([prices]) (creator, 2026-11-18). */
	fun outputValue(prices: MarketPrices): Int = size * prices.averagePrice(goods)

	/** This factory with [units] more units a turn (R46): there is no cap. */
	fun addUnits(units: Int): Factory = copy(size = size + units)

	/**
	 * The public log line of [corporation] collecting this factory's output for a turn, [stored] units of it kept (R50,
	 * E06-S07-1): "Orion Freight collected 4 u of ISO at Tau Ceti (2 lost)". The rest of its [size] was lost; nothing is
	 * said about it when all was kept.
	 */
	fun collectLog(corporation: String, stored: Int): String {
		val lost = size - stored
		return "$corporation collected $stored u of ${goods.name} at ${system.displayName}" + if (lost > 0) " ($lost lost)" else ""
	}
}
