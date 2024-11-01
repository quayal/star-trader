package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.OfferType;

public record TransactionTradeOffer(
        TradeOffer tradeOffer,
        OfferType offerType,
        int amount) {
}
