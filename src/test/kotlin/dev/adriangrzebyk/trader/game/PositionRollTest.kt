package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PositionRollTest {

	private val muHerculisIso = MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)

	@Test
	fun shouldSayWhatTheRollChanged() {
		// E05-S06-4
		assertThat(PositionRoll(5, MarketTier.WHOLESALER, emptyList(), null).outcome(muHerculisIso))
			.isEqualTo("rolled 5: now Wholesaler of ISO at Mu Herculis")
		assertThat(PositionRoll(9, null, emptyList(), null).outcome(muHerculisIso)).isEqualTo("rolled 9: no change")
	}

	@Test
	fun shouldTellTheLogWhoRolledAndWhomItDemoted() {
		// E05-S06-4, S06-3, E12-S02-6
		val names = CorporationNames(mapOf("altair@example.com" to "Altair Trade Co.", "orion@example.com" to "Orion Freight"))
		val gammaPoly = MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY)
		val roll = PositionRoll(6, MarketTier.DICTATOR, listOf("orion@example.com"), 4)

		assertThat(roll.logText("altair@example.com", 6, gammaPoly, names)).isEqualTo(
			"Altair Trade Co. traded 6 u — rolled 6: now Dictator of POLY at Gamma Leporis; business connections rise to 4; " +
				"Orion Freight is demoted to Wholesaler"
		)
		assertThat(PositionRoll(9, null, emptyList(), null).logText("altair@example.com", 7, gammaPoly, names))
			.isEqualTo("Altair Trade Co. traded 7 u — rolled 9: no change")
	}

	@Test
	fun shouldClimbTheTiersUpToDictator() {
		// R43, S06-3
		assertThat(MarketTier.RETAILER.next()).isEqualTo(MarketTier.WHOLESALER)
		assertThat(MarketTier.WHOLESALER.next()).isEqualTo(MarketTier.DICTATOR)
		assertThat(MarketTier.DICTATOR.next()).isNull()
	}
}
