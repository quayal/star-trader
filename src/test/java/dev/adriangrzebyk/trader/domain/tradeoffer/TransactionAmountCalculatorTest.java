package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.player.Player;
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
    void validBuyOfferWithPositiveSDAndPositivePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                GAMMA_LEPORIS,
                POLY,
                BUY,
                1,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(7);
    }

    @Test
    void validBuyOfferWithPositiveSDAndNegativePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                EPSILON_ERIDANI,
                ISO,
                BUY,
                9,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 10)).isEqualTo(1);
    }

    @Test
    void validBuyOfferWithNegativeSDAndPositivePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                TAU_CETI,
                ISO,
                BUY,
                6,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 7)).isEqualTo(3);
    }

    @Test
    void validSellOfferWithNegativeSDAndNegativePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                SIGMA_DRACONIS,
                CHIPS,
                SELL,
                12,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 3)).isEqualTo(12);
    }

    @Test
    void validSellOfferWithNegativeSDAndPositivePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                EPSILON_ERIDANI,
                POLY,
                SELL,
                6,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(1);
    }

    @Test
    void validSellOfferWithPositiveSDAndNegativePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                MU_HERCULIS,
                CHIPS,
                SELL,
                9,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 12)).isEqualTo(2);
    }

    @Test
    void invalidBuyOfferWithPositiveSDAndNegativePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                SIGMA_DRACONIS,
                ISO,
                BUY,
                2,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(-2);
    }

    @Test
    void invalidBuyOfferWithNegativeSDAndNegativePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                SIGMA_DRACONIS,
                CHIPS,
                BUY,
                12,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(-9);
    }

    @Test
    void invalidBuyOfferWithNegativeSDAndPositivePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                SIGMA_DRACONIS,
                CHIPS,
                BUY,
                15,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 4)).isEqualTo(-1);
    }

    @Test
    void invalidBuyOfferForPositiveSDAndNegativePriceModifierMatchingSD() {
        TradeOffer offer = new TradeOffer(1,
                1,
                EPSILON_ERIDANI,
                FOOD,
                BUY,
                13,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 10)).isEqualTo(0);
    }

    @Test
    void invalidSellOfferWithNegativeSDAndPositivePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                MU_HERCULIS,
                POLY,
                SELL,
                10,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(-3);

    }

    @Test
    void invalidSellOfferWithPositiveSDAndPositivePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                SIGMA_DRACONIS,
                FOOD,
                SELL,
                11,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(-7);

    }

    @Test
    void invalidSellOfferWithPositiveSDAndNegativePriceModifier() {
        TradeOffer offer = new TradeOffer(1,
                1,
                SIGMA_DRACONIS,
                FOOD,
                SELL,
                9,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(-1);

    }

    @Test
    void invalidSellOfferForNegativeSDAndNegativePriceModifierMatchingSD() {
        TradeOffer offer = new TradeOffer(1,
                1,
                TAU_CETI,
                POLY,
                SELL,
                7,
                1);

        assertThat(transactionAmountCalculator.getAmountToTrade(offer, 8)).isEqualTo(0);
    }
}