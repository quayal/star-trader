package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AwaitedTradeTest {

	private val names = CorporationNames(mapOf("vega@example.com" to "Vega Lines"))
	private val awaited = AwaitedTrade(Offer("vega@example.com", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11), 21)

	@Test
	fun shouldSayWhomTheCounterWaitsForWithTheMarketsMaxOnly() {
		// S01-8 (D09): the counter's max, never the trader's own (R29)
		assertThat(awaited.waitingText(names)).isEqualTo("Vega Lines can buy up to 21 u — waiting for their decision.")
	}

	@Test
	fun shouldBeForThePlayerWhoseOfferItIs() {
		assertThat(awaited.isFor("vega@example.com")).isTrue()
		assertThat(awaited.isFor("orion@example.com")).isFalse()
	}
}
