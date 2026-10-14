package dev.adriangrzebyk.trader.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SDCalculatorTest {

	@ParameterizedTest
	@MethodSource("provideTestData")
	void getPriceModifier(int sDIndicator, int priceModifier) {
		SDCalculator sdCalculator = new SDCalculator();
		assertThat(sdCalculator.getPriceModifier(sDIndicator)).isEqualTo(priceModifier);
	}

	static Stream<Arguments> provideTestData() {
		return Stream.of(
				arguments(11, -4),
				arguments(-11, 4)
		);
	}
}