package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.OfferType;
import dev.adriangrzebyk.trader.domain.StarSystem;

import java.util.Objects;
import java.util.UUID;


public record TradeOffer(int turn,
                         int playerId,
                         StarSystem system,
                         Goods goods,
                         OfferType offerType,
                         int price,
                         int initiative) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeOffer offer = (TradeOffer) o;
        return turn == offer.turn && playerId == offer.playerId && goods == offer.goods && system == offer.system && offerType == offer.offerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(turn, playerId, system, goods, offerType);
    }
}