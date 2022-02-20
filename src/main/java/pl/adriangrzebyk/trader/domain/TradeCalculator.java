package pl.adriangrzebyk.trader.domain;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TradeCalculator {
	private final StarSystem system;
	private final Map<Goods, Integer> indicators;
	private final Map<Goods, Integer> prices;

	public TradeCalculator(StarSystem system, Map<Goods, Integer> indicators, Map<Goods, Integer> prices) {
		this.system = system;
		this.indicators = indicators;
		this.prices = prices;
	}

	TradeOffer getTheBestOffer(Goods goods, Set<TradeOffer> offers, int diceRoll) {
		int indicator = diceRoll + indicators.get(goods);
		TradeOffer winning = null;

		Set<TradeOffer> offersForGoods = offers.stream()
				.filter(tradeOffer -> tradeOffer.getGoods().equals(goods))
				.filter(tradeOffer -> tradeOffer.getSystem().equals(system))
				.collect(Collectors.toSet());

		if ( indicator >= 0 ) {
			winning = offersForGoods.stream()
					.filter(TradeOffer::isBuy)
					.reduce(this::getBetterBuy)
					.orElse(TradeOffer.Builder.getInstance().build());
		}
		return winning;
	}



	TradeOffer getBetterBuy(TradeOffer offer, TradeOffer other) {
		if (offer.getPrice() != other.getPrice()) {
			return offer.getPrice() < other.getPrice() ? other : offer;
		} return offer.getInitiative() < other.getInitiative() ? offer : other;
	}
}
