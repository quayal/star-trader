package pl.adriangrzebyk.trader.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DiceTest {

	@Test
	void shouldGenerateRandom2DRoll() {
		for (int i = 0; i < 100; i++) {
			int result = Dice.roll2D();
			assertThat(result).isGreaterThan(1).isLessThan(13);
//			System.out.println(result);
		}
	}

}