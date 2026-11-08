package dev.adriangrzebyk.trader.game

/**
 * Each good's price at each system in one game (R35): it starts at the board's and changes once each time that good
 * resolves in Trading, and only the price carries over to the next turn (the S/D indicator never changes). Public, like
 * the board.
 */
data class MarketPrices(val prices: Map<MarketGood, Int>) {

	/** [good]'s price now, which is also its start price the next time it resolves (R34). */
	fun priceOf(good: MarketGood): Int = prices.getValue(good)

	/** These prices with [good] now at [price] (R35). */
	fun withPrice(good: MarketGood, price: Int): MarketPrices = copy(prices = prices + (good to price))

	companion object {
		/** Every good at every system at the board's price: where every game starts. */
		fun board(): MarketPrices = MarketPrices(MarketGood.everyOnBoard().associateWith { it.boardPrice() })
	}
}
