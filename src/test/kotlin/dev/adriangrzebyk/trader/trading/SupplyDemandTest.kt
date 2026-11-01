package dev.adriangrzebyk.trader.trading

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SupplyDemandTest {

	@ParameterizedTest
	@CsvSource(
		"-11, 4",
		"11, -4"
	)
	fun `an indicator has its price modifier`(indicator: Int, priceModifier: Int) {
		assertThat(SupplyDemand(indicator).priceModifier()).isEqualTo(priceModifier)
	}

	@Test
	fun `an indicator off the table has no price modifier`() {
		assertThat(SupplyDemand(19).priceModifier()).isNull()
	}

	@Test
	fun `a price modifier spans a range of indicators, lowest for buying and highest for selling`() {
		assertThat(SupplyDemand.lowestFor(-4)).isEqualTo(SupplyDemand(11))
		assertThat(SupplyDemand.highestFor(-4)).isEqualTo(SupplyDemand(13))
	}
}
