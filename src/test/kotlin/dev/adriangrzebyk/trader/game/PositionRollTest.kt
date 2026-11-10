package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PositionRollTest {

	private val muHerculisIso = MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)

	@Test
	fun shouldSayWhatTheRollChanged() {
		// E05-S06-4
		assertThat(PositionRoll(5, MarketTier.WHOLESALER, emptyList()).outcome(muHerculisIso))
			.isEqualTo("rolled 5: now Wholesaler of ISO at Mu Herculis")
		assertThat(PositionRoll(9, null, emptyList()).outcome(muHerculisIso)).isEqualTo("rolled 9: no change")
	}

	@Test
	fun shouldClimbTheTiersUpToDictator() {
		// R43, S06-3
		assertThat(MarketTier.RETAILER.next()).isEqualTo(MarketTier.WHOLESALER)
		assertThat(MarketTier.WHOLESALER.next()).isEqualTo(MarketTier.DICTATOR)
		assertThat(MarketTier.DICTATOR.next()).isNull()
	}
}
