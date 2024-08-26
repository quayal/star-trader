package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.StarSystem;
import dev.adriangrzebyk.trader.util.Dice;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@AllArgsConstructor
class TradeOfferService {
    private BestOfferCalculator bestOfferCalculator;
    private TransactionAmountCalculator transactionAmountCalculator;

    TransactionTradeOffer getWinningOffer(StarSystem system, Goods goods, Set<TradeOffer> offers) {
        TradeOffer winningOffer = bestOfferCalculator.getWinningOffer(system, goods, offers, Dice.roll2D());

        int amountToTrade = transactionAmountCalculator.getAmountToTrade(winningOffer);

        return new TransactionTradeOffer(winningOffer, winningOffer.offerType(), amountToTrade);
    }
}

