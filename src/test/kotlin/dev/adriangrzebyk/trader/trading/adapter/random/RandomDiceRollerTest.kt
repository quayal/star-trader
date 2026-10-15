package dev.adriangrzebyk.trader.trading.adapter.random

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RandomDiceRollerTest {

	private val diceRoller = RandomDiceRoller()

	@Test
	fun shouldGenerateRandom2DRoll() {
		repeat(100) {
			val result = diceRoller.roll2D()
			assertThat(result).isGreaterThan(1).isLessThan(13)
		}
	}

	@Test
	fun shouldGenerateRandom3DRoll() {
		repeat(100) {
			val result = diceRoller.roll3D()
			assertThat(result).isGreaterThan(2).isLessThan(19)
		}
	}
}
