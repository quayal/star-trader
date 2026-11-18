package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CorporationNamesTest {

	private val names = CorporationNames(
		mapOf("orion@example.com" to "Orion Freight", "vega@example.com" to "Vega Lines", "kessler@example.com" to "Kessler Cargo")
	)

	@Test
	fun shouldNameAPlayerByTheirCorporation() {
		assertThat(names.of("vega@example.com")).isEqualTo("Vega Lines")
	}

	@Test
	fun shouldListSeveralPlayersWithAndBeforeTheLast() {
		assertThat(names.list(listOf("orion@example.com"))).isEqualTo("Orion Freight")
		assertThat(names.list(listOf("vega@example.com", "kessler@example.com"))).isEqualTo("Vega Lines and Kessler Cargo")
		assertThat(names.list(listOf("orion@example.com", "vega@example.com", "kessler@example.com")))
			.isEqualTo("Orion Freight, Vega Lines and Kessler Cargo")
	}

	@Test
	fun shouldSayWhoWonAndWithHowMuch() {
		// S08-5, S08-4
		assertThat(names.winText(listOf("orion@example.com"), 300)).isEqualTo("Game over: Orion Freight wins with 300 HT")
		assertThat(names.winText(listOf("vega@example.com", "kessler@example.com"), 2310))
			.isEqualTo("Game over: Vega Lines and Kessler Cargo win with 2,310 HT")
	}
}
