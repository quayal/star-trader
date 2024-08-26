package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.StarSystem;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

import static dev.adriangrzebyk.trader.util.Dice.roll2D;

@Service
@AllArgsConstructor
class TradeOfferService {
    private BestOfferCalculator bestOfferCalculator;
    private TransactionAmountCalculator transactionAmountCalculator;

    TransactionTradeOffer getWinningOffer(StarSystem system, Goods goods, Set<TradeOffer> offers) {
        int diceRoll = roll2D();
        TradeOffer winningOffer = bestOfferCalculator.getWinningOffer(system, goods, offers, diceRoll);

        int amountToTrade = transactionAmountCalculator.getAmountToTrade(winningOffer, diceRoll);

        return new TransactionTradeOffer(winningOffer, winningOffer.offerType(), amountToTrade);
    }
}

