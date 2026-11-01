package dev.adriangrzebyk.trader.trading

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SDCalculatorTest {

	@ParameterizedTest
	@CsvSource(
		"-11, 4",
		"11, -4"
	)
	fun getPriceModifier(sDIndicator: Int, priceModifier: Int) {
		assertThat(SDCalculator.getPriceModifier(sDIndicator)).isEqualTo(priceModifier)
	}
}
