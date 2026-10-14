package pl.adriangrzebyk.trader.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TradeCalculatorTest {

	private lateinit var tradeCalculator: TradeCalculator

	@BeforeEach
	fun setUp() {
		tradeCalculator = TradeCalculator(StarSystem.BETA_HYDRI, prepareIndicators(), preparePrices())
	}

	private fun preparePrices(): Map<Goods, Int> = mapOf(
		Goods.POLY to 7,
		Goods.ISO to 8,
		Goods.CHIPS to 17,
		Goods.FOOD to 20,
	)

	private fun prepareIndicators(): Map<Goods, Int> = mapOf(
		Goods.POLY to -8,
		Goods.ISO to -8,
		Goods.FOOD to -9,
		Goods.CHIPS to -10,
	)

	private val betaPolyBuyNine = TradeOffer(
		offerType = OfferType.BUY,
		player = Player("Adrian"),
		goods = Goods.POLY,
		price = 9,
		system = StarSystem.BETA_HYDRI,
		initiative = 0,
	)

	private val betaPolyBuyTen = TradeOffer(
		offerType = OfferType.BUY,
		player = Player("Mateusz"),
		goods = Goods.POLY,
		price = 10,
		system = StarSystem.BETA_HYDRI,
		initiative = 1,
	)

	private val betaPolySellSeven = TradeOffer(
		offerType = OfferType.SELL,
		player = Player("Adrian"),
		goods = Goods.POLY,
		price = 7,
		system = StarSystem.BETA_HYDRI,
		initiative = 0,
	)

	private val betaPolySellEight = TradeOffer(
		offerType = OfferType.SELL,
		player = Player("Mateusz"),
		goods = Goods.POLY,
		price = 8,
		system = StarSystem.BETA_HYDRI,
		initiative = 1,
	)

	@Test
	fun shouldChooseTheBestBuyOffer() {
		val offers = setOf(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven)
		val result = tradeCalculator.getTheBestOffer(Goods.POLY, offers, 10)
		assertThat(result).isEqualTo(betaPolyBuyTen)
	}
}
