package dev.adriangrzebyk.trader.login.adapter.memory

import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryOneTimeCodeStore : OneTimeCodeStore {

	private val codesByEmail = ConcurrentHashMap<String, String>()

	override fun store(email: String, code: String) {
		codesByEmail[email] = code
	}

	override fun codeFor(email: String): String? = codesByEmail[email]
}
