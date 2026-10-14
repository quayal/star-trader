package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.StarSystem;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

import static dev.adriangrzebyk.trader.util.Dice.roll2D;
import static java.util.stream.Collectors.toSet;

@Service
@AllArgsConstructor
class TradeOfferService {
    private BestOfferCalculator bestOfferCalculator;
    private TransactionAmountCalculator transactionAmountCalculator;
    private TradeOfferRepository tradeOfferRepository;

    TransactionTradeOffer getWinningOffer(StarSystem system, Goods goods, Set<TradeOffer> offers) {
        int diceRoll = roll2D();

        TradeOffer winningOffer = bestOfferCalculator.getWinningOffer(system, goods, filterForSystemAndGoods(system, goods, offers), diceRoll);

        int amountToTrade = transactionAmountCalculator.getAmountToTrade(winningOffer, diceRoll);

        return new TransactionTradeOffer(winningOffer, winningOffer.offerType(), amountToTrade);
    }

    boolean registerTradeOffer(TradeOffer tradeOffer) {
            return tradeOfferRepository.save(tradeOffer);
    }

    Set<TradeOffer> getOffersForGoodsInSystem(int turn, Goods goods, StarSystem system) {
        Set<TradeOffer> offersForTurn = tradeOfferRepository.getOffersForTurn(turn);
        return filterForSystemAndGoods(system, goods, offersForTurn);
    }

    private Set<TradeOffer> filterForSystemAndGoods(StarSystem system, Goods goods, Set<TradeOffer> offers) {
        return offers.stream()
                .filter(tradeOffer -> tradeOffer.goods() == goods)
                .filter(tradeOffer -> tradeOffer.system() == system)
                .collect(toSet());
    }
}

