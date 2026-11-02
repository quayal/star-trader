package dev.adriangrzebyk.trader.trading

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StarSystemTest {

	@Test
	fun shouldGiveEachSystemItsLocationStatistics() {
		assertThat(StarSystem.TAU_CETI.locations).isEqualTo(LocationStats(portClass = 4, legalSystem = 2, police = 3, security = 4))
	}

	@Test
	fun shouldKeepEveryLocationStatisticOnTheOneToFiveScale() {
		val values = StarSystem.entries.flatMap {
			listOf(it.locations.portClass, it.locations.legalSystem, it.locations.police, it.locations.security)
		}

		assertThat(values).allMatch { it in 1..5 }
	}
}
