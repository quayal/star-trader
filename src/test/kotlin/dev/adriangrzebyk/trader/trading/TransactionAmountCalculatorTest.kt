package dev.adriangrzebyk.trader.trading

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionAmountCalculatorTest {

	private val transactionAmountCalculator = TransactionAmountCalculator()

	@Test
	fun validBuyOfferWithPositiveSDAndPositivePriceModifier() {
		val offer = TradeOffer(system = StarSystem.GAMMA_LEPORIS, goods = Goods.POLY, offerType = OfferType.BUY, price = 1, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(7)
	}

	@Test
	fun validBuyOfferWithPositiveSDAndNegativePriceModifier() {
		val offer = TradeOffer(system = StarSystem.EPSILON_ERIDANI, goods = Goods.ISO, offerType = OfferType.BUY, price = 9, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 10)).isEqualTo(1)
	}

	@Test
	fun validBuyOfferWithNegativeSDAndPositivePriceModifier() {
		val offer = TradeOffer(system = StarSystem.TAU_CETI, goods = Goods.ISO, offerType = OfferType.BUY, price = 6, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 7)).isEqualTo(3)
	}

	@Test
	fun validSellOfferWithNegativeSDAndNegativePriceModifier() {
		val offer = TradeOffer(system = StarSystem.SIGMA_DRACONIS, goods = Goods.CHIPS, offerType = OfferType.SELL, price = 12, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 3)).isEqualTo(12)
	}

	@Test
	fun validSellOfferWithNegativeSDAndPositivePriceModifier() {
		val offer = TradeOffer(system = StarSystem.EPSILON_ERIDANI, goods = Goods.POLY, offerType = OfferType.SELL, price = 6, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(1)
	}

	@Test
	fun validSellOfferWithPositiveSDAndNegativePriceModifier() {
		val offer = TradeOffer(system = StarSystem.MU_HERCULIS, goods = Goods.CHIPS, offerType = OfferType.SELL, price = 9, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 12)).isEqualTo(2)
	}

	@Test
	fun invalidBuyOfferWithPositiveSDAndNegativePriceModifier() {
		val offer = TradeOffer(system = StarSystem.SIGMA_DRACONIS, goods = Goods.ISO, offerType = OfferType.BUY, price = 2, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(-2)
	}

	@Test
	fun invalidBuyOfferWithNegativeSDAndNegativePriceModifier() {
		val offer = TradeOffer(system = StarSystem.SIGMA_DRACONIS, goods = Goods.CHIPS, offerType = OfferType.BUY, price = 12, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(-9)
	}

	@Test
	fun invalidBuyOfferWithNegativeSDAndPositivePriceModifier() {
		val offer = TradeOffer(system = StarSystem.SIGMA_DRACONIS, goods = Goods.CHIPS, offerType = OfferType.BUY, price = 15, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(-1)
	}

	@Test
	fun invalidBuyOfferForPositiveSDAndNegativePriceModifierMatchingSD() {
		val offer = TradeOffer(system = StarSystem.EPSILON_ERIDANI, goods = Goods.FOOD, offerType = OfferType.BUY, price = 13, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 10)).isEqualTo(0)
	}

	@Test
	fun invalidSellOfferWithNegativeSDAndPositivePriceModifier() {
		val offer = TradeOffer(system = StarSystem.MU_HERCULIS, goods = Goods.POLY, offerType = OfferType.SELL, price = 10, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(-3)
	}

	@Test
	fun invalidSellOfferWithPositiveSDAndPositivePriceModifier() {
		val offer = TradeOffer(system = StarSystem.SIGMA_DRACONIS, goods = Goods.FOOD, offerType = OfferType.SELL, price = 11, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(-7)
	}

	@Test
	fun invalidSellOfferWithPositiveSDAndNegativePriceModifier() {
		val offer = TradeOffer(system = StarSystem.SIGMA_DRACONIS, goods = Goods.FOOD, offerType = OfferType.SELL, price = 9, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(-1)
	}

	@Test
	fun invalidSellOfferForNegativeSDAndNegativePriceModifierMatchingSD() {
		val offer = TradeOffer(system = StarSystem.TAU_CETI, goods = Goods.POLY, offerType = OfferType.SELL, price = 7, initiative = 1)
		assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(0)
	}
}
