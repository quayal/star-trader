package pl.adriangrzebyk.trader.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BestOfferCalculatorTest {

	private val bestOfferCalculator = BestOfferCalculator()

	private val betaPolyBuyNine = TradeOffer(
		player = Player("Adrian"),
		system = StarSystem.BETA_HYDRI,
		goods = Goods.POLY,
		offerType = OfferType.BUY,
		price = 9,
		initiative = 0,
	)

	private val betaPolyBuyTen = TradeOffer(
		player = Player("Mateusz"),
		system = StarSystem.BETA_HYDRI,
		goods = Goods.POLY,
		offerType = OfferType.BUY,
		price = 10,
		initiative = 1,
	)

	private val betaPolySellSeven = TradeOffer(
		player = Player("Adrian"),
		system = StarSystem.BETA_HYDRI,
		goods = Goods.POLY,
		offerType = OfferType.SELL,
		price = 7,
		initiative = 0,
	)

	private val betaPolySellEight = TradeOffer(
		player = Player("Mateusz"),
		system = StarSystem.BETA_HYDRI,
		goods = Goods.POLY,
		offerType = OfferType.SELL,
		price = 8,
		initiative = 1,
	)

	private val betaPolyBuyTenWithLowerInitiative = TradeOffer(
		player = Player("Adrian"),
		system = StarSystem.BETA_HYDRI,
		goods = Goods.POLY,
		offerType = OfferType.BUY,
		price = 10,
		initiative = 0,
	)

	private val betaPolySellEightWithLowerInitiative = TradeOffer(
		player = Player("Adrian"),
		system = StarSystem.BETA_HYDRI,
		goods = Goods.POLY,
		offerType = OfferType.SELL,
		price = 8,
		initiative = 0,
	)

	@Test
	fun shouldChooseTheHighestBuyOffer() {
		val offers = setOf(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven)
		val result = bestOfferCalculator.getWinningOffer(StarSystem.BETA_HYDRI, Goods.POLY, offers, 8)
		assertThat(result).isEqualTo(betaPolyBuyTen)
	}

	@Test
	fun shouldChooseTheLowestInitiativeForBuyOffersIfPricesAreEqual() {
		val offers = setOf(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven, betaPolyBuyTenWithLowerInitiative)
		val result = bestOfferCalculator.getWinningOffer(StarSystem.BETA_HYDRI, Goods.POLY, offers, 8)
		assertThat(result).isEqualTo(betaPolyBuyTenWithLowerInitiative)
	}

	@Test
	fun shouldChooseTheLowestSellOffer() {
		val offers = setOf(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven)
		val result = bestOfferCalculator.getWinningOffer(StarSystem.BETA_HYDRI, Goods.POLY, offers, 7)
		assertThat(result).isEqualTo(betaPolySellSeven)
	}

	@Test
	fun shouldChooseTheLowestInitiativeForSellOffersIfPricesAreEqual() {
		val offers = setOf(betaPolyBuyNine, betaPolySellEight, betaPolySellEightWithLowerInitiative)
		val result = bestOfferCalculator.getWinningOffer(StarSystem.BETA_HYDRI, Goods.POLY, offers, 7)
		assertThat(result).isEqualTo(betaPolySellEightWithLowerInitiative)
	}
}
