package dev.adriangrzebyk.trader.trading

private val indicatorByModifier: Map<Int, Int> = mapOf(
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

internal fun getPriceModifier(sDIndicator: Int): Int? = indicatorByModifier[sDIndicator]

internal fun getIndicatorForBuying(priceModifier: Int): Int = indicatorsForModifier(priceModifier).min()

internal fun getIndicatorForSelling(priceModifier: Int): Int = indicatorsForModifier(priceModifier).max()

private fun indicatorsForModifier(priceModifier: Int): List<Int> = indicatorByModifier.filterValues { it == priceModifier }.keys.toList()
