package dev.adriangrzebyk.trader.trading

/** [takesCargoSpace]: R42, POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
enum class Goods(val takesCargoSpace: Boolean) {
	CHIPS(false),
	FOOD(false),
	ISO(true),
	POLY(true)
}
