package dev.adriangrzebyk.trader.trading

/** A supply/demand (S/D) indicator, and how it relates to the price modifier of an offer. */
@JvmInline
internal value class SupplyDemand(val indicator: Int) {

	/** The price modifier for this indicator, or null when the indicator is off the table. */
	fun priceModifier(): Int? = PRICE_MODIFIER_BY_INDICATOR[indicator]

	companion object {

		private val PRICE_MODIFIER_BY_INDICATOR: Map<Int, Int> = mapOf(
			-18 to 7,
			-17 to 6,
			-16 to 6,
			-15 to 6,
			-14 to 5,
			-13 to 5,
			-12 to 5,
			-11 to 4,
			-10 to 4,
			-9 to 4,
			-8 to 3,
			-7 to 3,
			-6 to 3,
			-5 to 2,
			-4 to 2,
			-3 to 1,
			-2 to 1,
			-1 to 0,
			0 to 0,
			1 to 0,
			2 to -1,
			3 to -1,
			4 to -1,
			5 to -2,
			6 to -2,
			7 to -2,
			8 to -3,
			9 to -3,
			10 to -3,
			11 to -4,
			12 to -4,
			13 to -4,
			14 to -5,
			15 to -5,
			16 to -5,
			17 to -5,
			18 to -6
		)

		/** The lowest indicator with [priceModifier]: what a buy is measured against. */
		fun lowestFor(priceModifier: Int): SupplyDemand = SupplyDemand(indicatorsFor(priceModifier).min())

		/** The highest indicator with [priceModifier]: what a sell is measured against. */
		fun highestFor(priceModifier: Int): SupplyDemand = SupplyDemand(indicatorsFor(priceModifier).max())

		private fun indicatorsFor(priceModifier: Int): Set<Int> = PRICE_MODIFIER_BY_INDICATOR.filterValues { it == priceModifier }.keys
	}
}
