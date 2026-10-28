package dev.adriangrzebyk.trader.login.adapter.random

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RandomOneTimeCodeGeneratorTest {

	private val generator = RandomOneTimeCodeGenerator()

	@Test
	fun shouldGenerateASixDigitNumericCode() {
		repeat(100) {
			val code = generator.generate()
			assertThat(code).hasSize(6).matches("\\d{6}")
		}
	}
}
