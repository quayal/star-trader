package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Player;
import dev.adriangrzebyk.trader.domain.SDCalculator;
import org.junit.jupiter.api.Test;

import static dev.adriangrzebyk.trader.domain.Goods.*;
import static dev.adriangrzebyk.trader.domain.OfferType.BUY;
import static dev.adriangrzebyk.trader.domain.OfferType.SELL;
import static dev.adriangrzebyk.trader.domain.StarSystem.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TransactionAmountCalculatorTest {

    TransactionAmountCalculator transactionAmountCalculator = new TransactionAmountCalculator(new SDCalculator());

    @Test
    void shouldGivePositiveAmountForBuyOffer() {

        TradeOffer offer = new TradeOffer(
                new Player("tester"),
                SIGMA_DRACONIS,
                ISO,
                BUY,
                8,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(18);
    }

    @Test
    void shouldGivePositiveAmountForSellOffer() {

        TradeOffer offer = new TradeOffer(
                new Player("tester"),
                SIGMA_DRACONIS,
                CHIPS,
                SELL,
                12,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 3)).isEqualTo(12);

    }

    @Test
    void shouldGiveNegativeAmountForBuyOffer() {

        TradeOffer offer = new TradeOffer(
                new Player("tester"),
                SIGMA_DRACONIS,
                ISO,
                BUY,
                2,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(-2);
    }

    @Test
    void shouldGiveNegativeAmountForSellOffer() {

        TradeOffer offer = new TradeOffer(
                new Player("tester"),
                MU_HERCULIS,
                POLY,
                SELL,
                10,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(-3);

    }

    @Test
    void shouldGiveZeroForBuyOffer() {

        TradeOffer offer = new TradeOffer(
                new Player("tester"),
                EPSILON_ERIDANI,
                FOOD,
                BUY,
                13,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 10)).isEqualTo(0);
    }

    @Test
    void shouldGiveZeroForSellOffer() {
        TradeOffer offer = new TradeOffer(
                new Player("tester"),
                TAU_CETI,
                POLY,
                SELL,
                7,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(0);
    }
}