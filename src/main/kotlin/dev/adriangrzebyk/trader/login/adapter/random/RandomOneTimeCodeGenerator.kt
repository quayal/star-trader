package dev.adriangrzebyk.trader.login.adapter.random

import dev.adriangrzebyk.trader.login.OneTimeCodeGenerator
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class RandomOneTimeCodeGenerator : OneTimeCodeGenerator {

	override fun generate(): String = (0 until CODE_LENGTH).joinToString("") { Random.nextInt(0, 10).toString() }

	companion object {
		private const val CODE_LENGTH = 6
	}
}
