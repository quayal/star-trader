package pl.adriangrzebyk.trader.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TradeOfferSequenceTest {

	@Test
	fun shouldIncrementSequenceOnce() {
		val sequence = TradeOfferSequence()
		assertThat(sequence.next()).isEqualTo(1)
	}

	@Test
	fun shouldIncrementSequenceFiveTimes() {
		val sequence = TradeOfferSequence()

		sequence.next()
		sequence.next()
		sequence.next()
		sequence.next()

		assertThat(sequence.next()).isEqualTo(5)
	}
}
