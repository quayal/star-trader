package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Player;
import dev.adriangrzebyk.trader.domain.SDCalculator;
import org.junit.jupiter.api.Test;

import static dev.adriangrzebyk.trader.domain.Goods.ISO;
import static dev.adriangrzebyk.trader.domain.OfferType.BUY;
import static dev.adriangrzebyk.trader.domain.StarSystem.SIGMA_DRACONIS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TransactionAmountCalculatorTest {

    @Test
    void shouldGiveTheCorrectAmountForABuyOffer() {

        TradeOffer offer = new TradeOffer(
                new Player("Mateusz"),
                SIGMA_DRACONIS,
                ISO,
                BUY,
                8,
                1);
        TransactionAmountCalculator transactionAmountCalculator = new TransactionAmountCalculator(new SDCalculator());

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(18);
    }

}