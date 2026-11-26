package dev.adriangrzebyk.trader.game.control

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R128: a ship in Space is caught when 2d6 + its bonus is police or less.
class PoliceOddsTest {

	@Test
	fun shouldCountTheRollsAtOrBelowPolice() {
		// 21 of 36 rolls are 7 or less; with a Battle comm's +2, 10 of 36 are 5 or less
		assertThat(PoliceOdds(0, 7).caughtPercent()).isEqualTo(58)
		assertThat(PoliceOdds(2, 7).caughtPercent()).isEqualTo(28)
		assertThat(PoliceOdds(0, 0).caughtPercent()).isEqualTo(0)
		assertThat(PoliceOdds(0, 12).caughtPercent()).isEqualTo(100)
	}
}
