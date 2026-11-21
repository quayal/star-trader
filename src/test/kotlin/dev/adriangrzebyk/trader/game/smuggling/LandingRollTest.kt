package dev.adriangrzebyk.trader.game.smuggling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R116, R117 (creator, 2026-11-20): 2d6 + bonuses against the Planet's security.
class LandingRollTest {

	@Test
	fun shouldLandAboveSecurityBeForcedIntoPortOnItAndBeSeizedBelowIt() {
		assertThat(LandingRoll(8, 0, 7).outcome()).isEqualTo(LandingOutcome.LANDED)
		assertThat(LandingRoll(7, 0, 7).outcome()).isEqualTo(LandingOutcome.FORCED_INTO_PORT)
		assertThat(LandingRoll(6, 0, 7).outcome()).isEqualTo(LandingOutcome.SEIZED)
		assertThat(LandingRoll(5, 5, 10).outcome()).isEqualTo(LandingOutcome.FORCED_INTO_PORT)
	}

	@Test
	fun shouldWordTheRollForTheLog() {
		assertThat(LandingRoll(6, 3, 10).text()).isEqualTo("6 + 3 = 9 vs security 10")
		assertThat(LandingRoll(9, 0, 7).text()).isEqualTo("9 vs security 7")
	}
}

class LandingOddsTest {

	@Test
	fun shouldGiveTheSpecsReferenceOdds() {
		// R118: Epsilon Eridani (security 9) with no bonus; Beta Hydri (10) with Aerodynamic + Battle comm (+5)
		assertThat(
			listOf(LandingOdds(0, 9).landsPercent(), LandingOdds(0, 9).forcedPercent(), LandingOdds(0, 9).seizedPercent())
		).containsExactly(17, 11, 72)
		assertThat(
			listOf(LandingOdds(5, 10).landsPercent(), LandingOdds(5, 10).forcedPercent(), LandingOdds(5, 10).seizedPercent())
		).containsExactly(72, 11, 17)
	}
}
