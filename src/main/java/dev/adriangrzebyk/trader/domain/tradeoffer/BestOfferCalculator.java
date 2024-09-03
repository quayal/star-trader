package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.StarSystem;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

import static dev.adriangrzebyk.trader.domain.OfferType.BUY;
import static dev.adriangrzebyk.trader.domain.OfferType.SELL;
@Service
class BestOfferCalculator {

    TradeOffer getWinningOffer(StarSystem system, Goods goods, Set<TradeOffer> offers, int diceRoll) {
        int indicator = diceRoll + system.getIndicator(goods);
        TradeOffer winning;

        // pass only the offers for the goods in this system -- you only call it for system and goods
        Set<TradeOffer> offersForGoods = offers.stream()
                .filter(tradeOffer -> tradeOffer.goods() == goods)
                .filter(tradeOffer -> tradeOffer.system() == system)
                .collect(Collectors.toSet());

        if (indicator >= 0) {
            winning = offersForGoods.stream()
                    .filter(tradeOffer -> tradeOffer.offerType() == BUY)
                    .reduce(this::getBetterBuy)
                    .orElseThrow(RuntimeException::new);
        } else {
            winning = offersForGoods.stream()
                    .filter(tradeOffer -> tradeOffer.offerType() == SELL)
                    .reduce(this::getBetterSell)
                    .orElseThrow(RuntimeException::new);
        }
        return winning;
    }


    private TradeOffer getBetterBuy(TradeOffer offer, TradeOffer other) {
        if (offer.price() != other.price()) {
            return offer.price() < other.price() ? other : offer;
        }
        return offer.initiative() < other.initiative() ? offer : other;
    }

    private TradeOffer getBetterSell(TradeOffer offer, TradeOffer other) {
        if (offer.price() != other.price()) {
            return offer.price() < other.price() ? offer : other;
        }
        return offer.initiative() < other.initiative() ? offer : other;
    }
}
