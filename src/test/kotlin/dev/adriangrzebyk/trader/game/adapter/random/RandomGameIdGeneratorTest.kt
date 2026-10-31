package dev.adriangrzebyk.trader.game.adapter.random

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RandomGameIdGeneratorTest {

	private val generator = RandomGameIdGenerator()

	@Test
	fun shouldNeverRepeatAnId() {
		val ids = List(10_000) { generator.generate() }

		assertThat(ids).doesNotHaveDuplicates().allMatch { it.length == 36 }
	}
}
