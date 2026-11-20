package dev.adriangrzebyk.trader.game.news

/**
 * Goods that only opportunities sell (R112), never traded on a market: the illegal [WEAPONS], [SLAVES] and [DRUGS], sold
 * on a black market at a d6 roll on the creator's table ([blackMarket], by roll 1 to 6), and legal [FURS], sold at a fixed
 * price. Each is held aboard ships only, as [hold] says (creator, 2026-11-20).
 */
enum class SpecialGoods(val displayName: String, val illegal: Boolean, val hold: Hold, val blackMarket: List<Int>) {
	WEAPONS("weapons", true, Hold.CARGO_SPACE, listOf(8, 10, 15, 25, 30, 40)),
	SLAVES("slaves", true, Hold.PASSENGER_SEAT, listOf(5, 8, 10, 15, 20, 25)),
	DRUGS("drugs", true, Hold.NONE, listOf(12, 15, 25, 35, 50, 70)),
	FURS("furs", false, Hold.NONE, emptyList());

	/** The black-market price per unit for [roll] (1 to 6, R112). */
	fun blackMarketPrice(roll: Int): Int = blackMarket[roll.coerceIn(1, blackMarket.size) - 1]

	/** What one unit takes aboard a ship. */
	enum class Hold { CARGO_SPACE, PASSENGER_SEAT, NONE }

	companion object {
		/** R112: the highest d6 result, which Frank's +2 can't pass. */
		const val MAX_ROLL = 6
	}
}
