package dev.adriangrzebyk.trader.domain.tradeoffer;

import lombok.Synchronized;
import org.springframework.stereotype.Component;

@Component
class TradeOfferSequence {
    private int counter;

    @Synchronized
    int next() {
        return ++counter;
    }
}
