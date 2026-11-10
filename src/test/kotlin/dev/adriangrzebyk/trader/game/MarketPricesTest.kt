package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
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
}
