package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods

/**
 * Permanent changes to every system's S/D indicator for a good (R115: E7 CHIPS +3, E8 FOOD +2, E9 ISO −3), on top of the
 * board's. A fall never takes an indicator below [FLOOR] (E9: "can't be lower than −10").
 */
data class SupplyDemandShifts(val shifts: Map<Goods, Int>) {

	/** [good]'s indicator with its shift: the board's plus the shift, a fall stopping at [FLOOR]. */
	fun indicatorOf(good: MarketGood): Int {
		val board = good.supplyDemand()
		val shifted = board + (shifts[good.goods] ?: 0)
		return if (shifted < board) maxOf(shifted, minOf(FLOOR, board)) else shifted
	}

	/** These shifts with [goods] moved by [delta] more. */
	fun shifted(goods: Goods, delta: Int): SupplyDemandShifts = copy(shifts = shifts + (goods to (shifts[goods] ?: 0) + delta))

	companion object {
		const val FLOOR = -10
		val NONE = SupplyDemandShifts(emptyMap())
	}
}
