package pl.adriangrzebyk.trader.util;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DiceTest {

	@Test
	void shouldGenerateRandom2DRoll() {
		for (int i = 0; i < 100; i++) {
			int result = Dice.roll2D();
			assertThat(result).isGreaterThan(1).isLessThan(13);
		}
	}

	@Test
	void shouldGenerateRandom3DRoll() {
		assertThat(IntStream.of(100)
				.map(count -> Dice.roll3D())
				.allMatch(result -> result > 3 && result < 19)).isTrue();
	}
}