package dev.adriangrzebyk.trader.game.adapter.random

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RandomDiceTest {

	private val dice = RandomDice(Random(42))

	@Test
	fun shouldRollOnlyOneOrTwoAndBothOfThem() {
		val rolls = List(1_000) { dice.d2() }.toSet()

		assertThat(rolls).containsExactlyInAnyOrder(1, 2)
	}
}
