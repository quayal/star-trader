package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// E05-S05, R35: each game's prices start at the board's and move one good at a time.
class MarketPricesTest {

	private val muHerculisIso = MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)

	@Test
	fun shouldStartEveryGoodAtEverySystemAtTheBoardsPrice() {
		val prices = MarketPrices.board()

		assertThat(prices.prices.keys).containsExactlyInAnyOrderElementsOf(MarketGood.everyOnBoard())
		assertThat(prices.priceOf(muHerculisIso)).isEqualTo(11)
	}

	@Test
	fun shouldChangeOnlyTheGoodWhosePriceMoved() {
		val prices = MarketPrices.board().withPrice(muHerculisIso, 10)

		assertThat(prices.priceOf(muHerculisIso)).isEqualTo(10)
		assertThat(prices.priceOf(MarketGood(StarSystem.MU_HERCULIS, Goods.CHIPS))).isEqualTo(12)
		assertThat(Game.lobby("game-1", 3, Player.newcomer("ada@example.com", "Ada", "Orion Freight")).prices).isEqualTo(MarketPrices.board())
	}

	@Test
	fun shouldKeepHowFarEachPriceMovedWhenItLastResolved() {
		// R140: the Market screen shows last turn's move per market
		val prices = MarketPrices.board().withPrice(muHerculisIso, 9).withPrice(muHerculisIso, 13)

		assertThat(prices.changeOf(muHerculisIso)).isEqualTo(4)
		assertThat(prices.changeOf(MarketGood(StarSystem.MU_HERCULIS, Goods.CHIPS))).isZero()
		assertThat(MarketPrices.board().withPrice(muHerculisIso, 8).changeOf(muHerculisIso)).isEqualTo(-3)
	}

	@Test
	fun shouldSayWhereAnOfferStopsCountingInFull() {
		// R15: a buy more than 7 above the price, or a sell more than 6 below it, counts as that limit; Mu Herculis ISO is 11
		val prices = MarketPrices.board()

		assertThat(prices.buyCeilingOf(muHerculisIso)).isEqualTo(18)
		assertThat(prices.sellFloorOf(muHerculisIso)).isEqualTo(5)
		assertThat(prices.withPrice(muHerculisIso, 14).buyCeilingOf(muHerculisIso)).isNull()
		assertThat(prices.withPrice(muHerculisIso, 6).sellFloorOf(muHerculisIso)).isNull()
	}
}
