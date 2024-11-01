package dev.adriangrzebyk.trader.domain.tradeoffer;

import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
class TradeOfferRepository {
    private final HashMap<Integer, Set<TradeOffer>> tradeOffers = new HashMap<>();

    boolean save(TradeOffer tradeOffer) {
        if (!tradeOffers.containsKey(tradeOffer.turn())) {
            tradeOffers.put(tradeOffer.turn(), new HashSet<>());
        }
        return tradeOffers.get(tradeOffer.turn()).add(tradeOffer);
    }

    Set<TradeOffer> getOffersForTurn(int turn) {
        return tradeOffers.get(turn);
    }
}
