package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.OfferType;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TransactionTradeOffer {
    private final TradeOffer tradeOffer;
    private final OfferType offerType;
    private final int amount;
}
