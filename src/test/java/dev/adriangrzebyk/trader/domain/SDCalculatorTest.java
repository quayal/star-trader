package dev.adriangrzebyk.trader.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SDCalculatorTest {

	@ParameterizedTest
	@MethodSource("provideTestData")
	void getPriceModifier(int sDIndicator, int priceModifier) {
		assertThat(SDCalculator.getPriceModifier(sDIndicator)).isEqualTo(priceModifier);
	}

	private static Object[][] provideTestData() {
		return new Object[][] {
				{ -11, 4 },
				{ 11, -4 }
		};
	}
}