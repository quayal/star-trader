package dev.adriangrzebyk.trader.domain.tradeoffer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TradeOfferSequenceTest {

    @Test
    public void shouldIncrementSequenceOnce() {

        TradeOfferSequence sequence = new TradeOfferSequence();
        long next = sequence.next();

        assertEquals(1L, next);
    }

    @Test
    public void shouldIncrementSequenceFiveTimes() {

        TradeOfferSequence sequence = new TradeOfferSequence();

        sequence.next();
        sequence.next();
        sequence.next();
        sequence.next();

        assertEquals(5L, sequence.next());
    }

}