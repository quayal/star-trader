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

	@ParameterizedTest
	@CsvSource(
		"4, -1",
		"-9, 4",
		"18, -6",
		"25, -6",
		"-18, 7",
		"-30, 7"
	)
	fun `a final counter moves the price by its modifier, saturating beyond the table (R35, R15)`(counter: Int, priceModifier: Int) {
		assertThat(SupplyDemand.priceModifierAt(counter)).isEqualTo(priceModifier)
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

	@ParameterizedTest
	@CsvSource(
		"0, -1",
		"7, -18",
		"12, -18",
		"-6, 18",
		"-10, 18"
	)
	fun `a buy is measured against the lowest indicator of its modifier, saturating at -6 and +7 (R15)`(priceModifier: Int, target: Int) {
		assertThat(SupplyDemand.buyTargetFor(priceModifier)).isEqualTo(SupplyDemand(target))
	}

	@ParameterizedTest
	@CsvSource(
		"-1, 4",
		"-6, 18",
		"-10, 18",
		"7, -18",
		"12, -18"
	)
	fun `a sell is measured against the highest indicator of its modifier, saturating at -6 and +7 (R15)`(priceModifier: Int, target: Int) {
		assertThat(SupplyDemand.sellTargetFor(priceModifier)).isEqualTo(SupplyDemand(target))
	}
}
