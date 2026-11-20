package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.board.StarSystem

/**
 * [units] of special [goods] aboard a ship (R112), bought through an opportunity and sold only at [sellAt]: on its
 * Planet's black market for illegal goods, in its Port for furs. Private to the ship's owner, like cargo.
 */
data class SpecialLot(val goods: SpecialGoods, val units: Int, val sellAt: StarSystem) {

	/** The cargo spaces it takes (R112): weapons one each, drugs, slaves and furs none. */
	fun cargoSpaces(): Int = if (goods.hold == SpecialGoods.Hold.CARGO_SPACE) units else 0

	/** The passenger seats it takes (R112): slaves one each. */
	fun passengerSeats(): Int = if (goods.hold == SpecialGoods.Hold.PASSENGER_SEAT) units else 0

	/** Whether it's the illegal kind that security confiscates (R117). */
	fun isIllegal(): Boolean = goods.illegal

	/** Whether this lot is [goods] bound for [system]: what one sale there sells (R112). */
	fun isFor(goods: SpecialGoods, system: StarSystem): Boolean = this.goods == goods && sellAt == system
}
