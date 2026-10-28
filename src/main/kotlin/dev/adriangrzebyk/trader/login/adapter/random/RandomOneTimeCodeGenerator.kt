package dev.adriangrzebyk.trader.login.adapter.random

import dev.adriangrzebyk.trader.login.OneTimeCodeGenerator
import org.springframework.stereotype.Component
import java.security.SecureRandom

@Component
class RandomOneTimeCodeGenerator : OneTimeCodeGenerator {

	private val random = SecureRandom()

	override fun generate(): String = random.nextInt(CODE_BOUND).toString().padStart(CODE_LENGTH, '0')

	companion object {
		private const val CODE_LENGTH = 6
		private const val CODE_BOUND = 1_000_000
	}
}
