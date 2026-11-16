package dev.adriangrzebyk.trader.game

/**
 * What a factory costs to build at a good's current market price [marketPrice] (R46): each unit of size costs
 * 5 × the price + 10 HT, paid once.
 */
@JvmInline
value class ProductionCost(val marketPrice: Int) {

	/** One unit of size. */
	fun perUnit(): Int = UNIT_PRICE_FACTOR * marketPrice + UNIT_BASE

	/** [units] units of size, as a Long so an absurd amount can't overflow before it is compared with the money. */
	fun of(units: Int): Long = units.toLong() * perUnit()

	private companion object {
		const val UNIT_PRICE_FACTOR = 5
		const val UNIT_BASE = 10
	}
}
