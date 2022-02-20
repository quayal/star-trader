package pl.adriangrzebyk.trader.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TradeCalculatorTest {

	private TradeCalculator tradeCalculator;

	@BeforeEach
	void setUp() {
		tradeCalculator = new TradeCalculator(StarSystem.BETA_HYDRI, prepareIndicators(), preparePrices());
	}

	private Map<Goods, Integer> preparePrices() {
		Map<Goods, Integer> prices = new HashMap<>();
		prices.put(Goods.POLY, 7);
		prices.put(Goods.ISO, 8);
		prices.put(Goods.CHIPS, 17);
		prices.put(Goods.FOOD, 20);
		return prices;
	}

	private Map<Goods, Integer> prepareIndicators() {
		Map<Goods, Integer> indicators = new HashMap<>();
		indicators.put(Goods.POLY, -8);
		indicators.put(Goods.ISO, -8);
		indicators.put(Goods.FOOD, -9);
		indicators.put(Goods.CHIPS, -10);
		return indicators;
	}

	private final TradeOffer betaPolyBuyNine = TradeOffer.Builder.getInstance()
			.withOfferType(OfferType.BUY)
			.byPlayer(new Player("Adrian"))
			.forGoods(Goods.POLY)
			.forPrice(9)
			.inSystem(StarSystem.BETA_HYDRI)
			.withInitiative(0)
			.build();

	private final TradeOffer betaPolyBuyTen = TradeOffer.Builder.getInstance()
			.withOfferType(OfferType.BUY)
			.byPlayer(new Player("Mateusz"))
			.forGoods(Goods.POLY)
			.forPrice(10)
			.inSystem(StarSystem.BETA_HYDRI)
			.withInitiative(1)
			.build();

	private final TradeOffer betaPolySellSeven = TradeOffer.Builder.getInstance()
			.withOfferType(OfferType.SELL)
			.byPlayer(new Player("Adrian"))
			.forGoods(Goods.POLY)
			.forPrice(7)
			.inSystem(StarSystem.BETA_HYDRI)
			.withInitiative(0)
			.build();

	private final TradeOffer betaPolySellEight = TradeOffer.Builder.getInstance()
			.withOfferType(OfferType.SELL)
			.byPlayer(new Player("Mateusz"))
			.forGoods(Goods.POLY)
			.forPrice(8)
			.inSystem(StarSystem.BETA_HYDRI)
			.withInitiative(1)
			.build();

	@Test
	void shouldChooseTheBestBuyOffer() {
		Set<TradeOffer> offers = Set.of(betaPolyBuyNine, betaPolyBuyTen, betaPolySellEight, betaPolySellSeven);
		TradeOffer result = tradeCalculator.getTheBestOffer(Goods.POLY, offers, 10);
		assertThat(result).isEqualTo(betaPolyBuyTen);
	}



}