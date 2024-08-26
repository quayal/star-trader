package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.OfferType;
import dev.adriangrzebyk.trader.domain.Player;
import dev.adriangrzebyk.trader.domain.StarSystem;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@AllArgsConstructor
public class TradeOfferFacade {
	private TradeOfferService tradeOfferService;

	public TradeOffer makeTradeOffer(StarSystem starSystem, Player player, Goods goods, int price, OfferType offerType, int initiative) {
		return new TradeOffer(player, starSystem, goods, offerType, price, initiative);
	}

	public TransactionTradeOffer getWinningOffer(StarSystem system, Goods goods, Set<TradeOffer> offers) {
		return tradeOfferService.getWinningOffer(system, goods, offers);
	}
}
