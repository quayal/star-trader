package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.OfferType;
import dev.adriangrzebyk.trader.domain.player.Player;
import dev.adriangrzebyk.trader.domain.StarSystem;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@AllArgsConstructor
public class TradeOfferFacade {
	private TradeOfferService tradeOfferService;
	private TradeOfferSequence sequence;

	boolean registerTradeOffer(TradeOffer tradeOffer) {
		return tradeOfferService.registerTradeOffer(tradeOffer);
	}

	public TransactionTradeOffer getWinningOffer(int turn, Goods goods, StarSystem system) {
		return tradeOfferService.getWinningOffer(system, goods, getOffersForGoodsInSystem(turn, goods, system));
	}

	Set<TradeOffer> getOffersForGoodsInSystem(int turn, Goods goods, StarSystem system) {
		return tradeOfferService.getOffersForGoodsInSystem(turn, goods, system);
	}
}
