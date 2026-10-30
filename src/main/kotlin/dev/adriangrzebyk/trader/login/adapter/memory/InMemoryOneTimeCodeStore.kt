package dev.adriangrzebyk.trader.login.adapter.memory

import dev.adriangrzebyk.trader.login.OneTimeCode
import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import dev.adriangrzebyk.trader.login.StoredCode
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryOneTimeCodeStore : OneTimeCodeStore {

	private val codesByEmail = ConcurrentHashMap<String, StoredCode>()

	override fun store(email: String, code: StoredCode) {
		val oldestValid = code.issuedAt.minus(OneTimeCode.TTL)
		codesByEmail.values.removeIf { it.issuedAt.isBefore(oldestValid) }
		codesByEmail[email] = code
	}

	override fun codeFor(email: String): StoredCode? = codesByEmail[email]

	override fun replace(email: String, expected: StoredCode, updated: StoredCode): Boolean = codesByEmail.replace(email, expected, updated)

	override fun consume(email: String, code: StoredCode): Boolean = codesByEmail.remove(email, code)
}
