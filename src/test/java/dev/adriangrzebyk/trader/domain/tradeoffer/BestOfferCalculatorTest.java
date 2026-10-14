package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import org.junit.jupiter.api.Test;

import java.util.*;

import static dev.adriangrzebyk.trader.domain.Goods.POLY;
import static dev.adriangrzebyk.trader.domain.OfferType.BUY;
import static dev.adriangrzebyk.trader.domain.OfferType.SELL;
import static dev.adriangrzebyk.trader.domain.StarSystem.BETA_HYDRI;
import static org.assertj.core.api.Assertions.assertThat;

class BestOfferCalculatorTest {

	private final BestOfferCalculator bestOfferCalculator = new BestOfferCalculator();

	private Map<Goods, Integer> preparePrices() {
		Map<Goods, Integer> prices = new HashMap<>();
		prices.put(POLY, 7);
		prices.put(Goods.ISO, 8);
		prices.put(Goods.CHIPS, 17);
		prices.put(Goods.FOOD, 20);
		return prices;
	}

	private Map<Goods, Integer> prepareIndicators() {
		Map<Goods, Integer> indicators = new HashMap<>();
		indicators.put(POLY, -8);
		indicators.put(Goods.ISO, -8);
		indicators.put(Goods.FOOD, -9);
		indicators.put(Goods.CHIPS, -10);
		return indicators;
	}

	private final TradeOffer betaPolyBuyNine = new TradeOffer(1,
			1,
			BETA_HYDRI,
			POLY,
			BUY,
			9,
			0);

	private final TradeOffer betaPolyBuyTen = new TradeOffer(1,
			2,
			BETA_HYDRI,
			POLY,
			BUY,
			10,
			1);

	private final TradeOffer betaPolySellSeven = new TradeOffer(1,
			1,
			BETA_HYDRI,
			POLY,
			SELL,
			7,
			0);

	private final TradeOffer betaPolySellEight = new TradeOffer(1,
			2,
			BETA_HYDRI,
			POLY,
			SELL,
			8,
			1);

	private final TradeOffer betaPolyBuyTenWithHigherInitiative = new TradeOffer(1,
			1,
			BETA_HYDRI,
			POLY,
			BUY,
			10,
			0);

	private final TradeOffer betaPolySellEightWithHigherInitiative = new TradeOffer(1,
			1,
			BETA_HYDRI,
			POLY,
			SELL,
			8,
			0);


	@Test
	void shouldChooseTheHighestBuyOffer() {
		Set<TradeOffer> offers = Set.of(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven);
		TradeOffer result = bestOfferCalculator.getWinningOffer(BETA_HYDRI, POLY, offers, 8);
		assertThat(result).isEqualTo(betaPolyBuyTen);
	}

	@Test
	void shouldChooseTheHighestInitiativeForBuyOffersIfPricesAreEqual() {
		Set<TradeOffer> offers = Set.of(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven, betaPolyBuyTenWithHigherInitiative);
		TradeOffer result = bestOfferCalculator.getWinningOffer(BETA_HYDRI, POLY, offers, 8);
		assertThat(result).isEqualTo(betaPolyBuyTenWithHigherInitiative);
	}

	@Test
	void shouldChooseTheLowestSellOffer() {
		Set<TradeOffer> offers = Set.of(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven);
		TradeOffer result = bestOfferCalculator.getWinningOffer(BETA_HYDRI, POLY, offers, 7);
		assertThat(result).isEqualTo(betaPolySellSeven);
	}

	@Test
	void shouldChooseTheHighestInitiativeForSellOffersIfPricesAreEqual() {
		Set<TradeOffer> offers = Set.of(betaPolyBuyNine, betaPolySellEight, betaPolySellEightWithHigherInitiative);
		TradeOffer result = bestOfferCalculator.getWinningOffer(BETA_HYDRI, POLY, offers, 7);
		assertThat(result).isEqualTo(betaPolySellEightWithHigherInitiative);
	}
}