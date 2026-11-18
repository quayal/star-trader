package dev.adriangrzebyk.trader.game.adapter.random

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RandomDiceTest {

	private val dice = RandomDice(Random(42))

	@Test
	fun shouldRollTwoD6FromTwoToTwelveAndEveryValueBetween() {
		val rolls = List(10_000) { dice.twoD6() }.toSet()

		assertThat(rolls).containsExactlyInAnyOrderElementsOf(2..12)
	}
}
