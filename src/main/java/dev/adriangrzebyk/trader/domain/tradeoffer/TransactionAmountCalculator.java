package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.SDCalculator;
import dev.adriangrzebyk.trader.domain.StarSystem;
import lombok.AllArgsConstructor;

import static java.lang.Math.abs;

@AllArgsConstructor
class TransactionAmountCalculator {
    private final SDCalculator sdCalculator;

    int getAmountToTrade(TradeOffer winningOffer, int diceRoll) {

        StarSystem system = winningOffer.system();
        Goods goods = winningOffer.goods();
        int askingPrice = winningOffer.price();
        int currentPrice = system.getPrice(goods);

        int supplyDemand = system.getIndicator(goods) + diceRoll;

        int priceModifier = askingPrice - currentPrice;

        int indicator = sdCalculator.getHighestAbsoluteSDIndicatorByModifier(priceModifier);

        return abs(indicator) + abs(supplyDemand);
    }
}
