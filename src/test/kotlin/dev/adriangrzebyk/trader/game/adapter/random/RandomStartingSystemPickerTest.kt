package dev.adriangrzebyk.trader.game.adapter.random

import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RandomStartingSystemPickerTest {

	private val picker = RandomStartingSystemPicker(Random(42))

	@Test
	fun shouldAlwaysPickTwoDifferentSystems() {
		val picks = List(1_000) { picker.pickTwoDifferentSystems() }

		assertThat(picks).allMatch { (first, second) -> first != second }
	}

	@Test
	fun shouldReachEverySystem() {
		val picked = List(1_000) { picker.pickTwoDifferentSystems() }.flatMap { it.toList() }.toSet()

		assertThat(picked).containsExactlyInAnyOrderElementsOf(StarSystem.entries)
	}
}
