package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem

/**
 * A player's factory for [goods] at [system] (R46): at most one per player per good per system, producing [size] units
 * a turn. [goods] is one the system can produce ([StarSystem.producibleGoods]). Public, like the board (R50).
 * [disabledTurns] is how many of its next Collects produce nothing after a sabotage (R99), 0 when it works.
 */
data class Factory(val system: StarSystem, val goods: Goods, val size: Int, val disabledTurns: Int) {

	/** Whether this is the factory for [goods] at [system]. */
	fun isFor(system: StarSystem, goods: Goods): Boolean = this.system == system && this.goods == goods

	/** Whether a sabotage stops this factory producing at its next Collect (R99). */
	fun isDisabled(): Boolean = disabledTurns > 0

	/** What this factory makes at a Collect (R47): its [size], or nothing while disabled (R99). */
	fun output(): Int = if (isDisabled()) 0 else size

	/** This factory once a Collect went by (R99): a disabled one has one turn fewer to go. */
	fun afterCollect(): Factory = if (isDisabled()) copy(disabledTurns = disabledTurns - 1) else this

	/** This factory disabled for [turns] more Collects (R99): a second sabotage adds its turns. */
	fun disabledFor(turns: Int): Factory = copy(disabledTurns = disabledTurns + turns)

	/** What one turn's output is worth at its good's mean price across the systems that trade it today ([prices]) (creator, 2026-11-18). */
	fun outputValue(prices: MarketPrices): Int = size * prices.averagePrice(goods)

	/** This factory with [units] more units a turn (R46): there is no cap. */
	fun addUnits(units: Int): Factory = copy(size = size + units)

	/** What selling one unit pays (R96): 5 × the good's current price at the factory's own system, [prices]. */
	fun salePricePerUnit(prices: MarketPrices): Int = SALE_PRICE_MULTIPLIER * prices.priceOf(MarketGood(system, goods))

	/** This factory with [units] fewer (R96), refused for more than it has or fewer than 1; null once it has none left. */
	fun withoutUnits(units: Int): Factory? {
		if (units < 1 || units > size) {
			throw InvalidInvestmentException("Enter a whole number of units from 1 to $size.")
		}
		return if (units == size) null else copy(size = size - units)
	}

	/**
	 * The public log line of [corporation] collecting this factory's output for a turn, [stored] units of it kept (R50,
	 * E06-S07-1): "Orion Freight collected 4 u of ISO at Tau Ceti (2 lost)". The rest of its [output] was lost; nothing is
	 * said about it when all was kept. A disabled factory made nothing (R99): "… collected nothing at Tau Ceti: its ISO
	 * factory is disabled".
	 */
	fun collectLog(corporation: String, stored: Int): String {
		if (isDisabled()) {
			return "$corporation collected nothing at ${system.displayName}: its ${goods.name} factory is disabled"
		}
		val lost = output() - stored
		return "$corporation collected $stored u of ${goods.name} at ${system.displayName}" + if (lost > 0) " ($lost lost)" else ""
	}

	/** The public log line of [units] sold by [corporation] (R96): "Vega Lines sold 2 u of its ISO factory at Tau Ceti". */
	fun soldLog(corporation: String, units: Int): String = "$corporation sold $units u of its ${goods.name} factory at ${system.displayName}"

	companion object {
		/** R96: a factory unit sells for this many times its good's price at its system. */
		const val SALE_PRICE_MULTIPLIER = 5

		/** A new factory of [size] units, working (R46). */
		fun built(system: StarSystem, goods: Goods, size: Int) = Factory(system, goods, size, 0)
	}
}
