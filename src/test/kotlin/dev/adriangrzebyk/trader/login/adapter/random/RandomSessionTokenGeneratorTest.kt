package dev.adriangrzebyk.trader.login.adapter.random

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RandomSessionTokenGeneratorTest {

	private val generator = RandomSessionTokenGenerator()

	@Test
	fun shouldNeverRepeatAToken() {
		val tokens = List(10_000) { generator.generate() }

		assertThat(tokens).doesNotHaveDuplicates().allMatch { it.length == 36 }
	}
}
