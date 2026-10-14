package pl.adriangrzebyk.trader.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiceTest {

	@Test
	fun shouldGenerateRandom2DRoll() {
		repeat(100) {
			val result = Dice.roll2D()
			assertThat(result).isGreaterThan(1).isLessThan(13)
		}
	}

	@Test
	fun shouldGenerateRandom3DRoll() {
		repeat(100) {
			val result = Dice.roll3D()
			assertThat(result).isGreaterThan(3).isLessThan(19)
		}
	}
}
