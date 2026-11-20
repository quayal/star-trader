package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import kotlin.math.roundToInt

/**
 * Each good's price at each system in one game (R35): it starts at the board's and changes once each time that good
 * resolves in Trading, and only the price carries over to the next turn (the S/D indicator never changes). Public, like
 * the board.
 */
data class MarketPrices(val prices: Map<MarketGood, Int>) {

	/** [good]'s price now, which is also its start price the next time it resolves (R34). */
	fun priceOf(good: MarketGood): Int = prices.getValue(good)

	/** [good]'s price now, or null when its system doesn't trade it. */
	fun priceAt(good: MarketGood): Int? = prices[good]

	/**
	 * [goods]'s mean price now across the systems that trade it, to the nearest HT (creator, 2026-11-18): what a held unit
	 * is worth wherever it ends up being sold. 0 when nowhere trades it.
	 */
	fun averagePrice(goods: Goods): Int =
		prices.filterKeys { it.goods == goods }.values.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0

	/**
	 * These prices with every good that [applies] moved by [delta] at once (R115: an event's price change), each kept from
	 * [Offer.MIN_PRICE] to [Offer.MAX_PRICE] (R35).
	 */
	fun shifted(delta: Int, applies: (MarketGood) -> Boolean): MarketPrices = copy(
		prices = prices.mapValues { (good, price) -> if (applies(good)) (price + delta).coerceIn(Offer.MIN_PRICE, Offer.MAX_PRICE) else price }
	)

	/** These prices with [good] now at [price] (R35). */
	fun withPrice(good: MarketGood, price: Int): MarketPrices = copy(prices = prices + (good to price))

	companion object {
		/** Every good at every system at the board's price: where every game starts. */
		fun board(): MarketPrices = MarketPrices(MarketGood.everyOnBoard().associateWith { it.boardPrice() })
	}
}
