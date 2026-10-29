package dev.adriangrzebyk.trader.login.adapter.memory

import dev.adriangrzebyk.trader.login.OneTimeCode
import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import dev.adriangrzebyk.trader.login.StoredCode
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryOneTimeCodeStore : OneTimeCodeStore {

	private val codesByEmail = ConcurrentHashMap<String, StoredCode>()

	override fun store(email: String, code: String, issuedAt: Instant) {
		val oldestValid = issuedAt.minus(OneTimeCode.TTL)
		codesByEmail.values.removeIf { it.issuedAt.isBefore(oldestValid) }
		codesByEmail[email] = StoredCode(code, issuedAt)
	}

	override fun codeFor(email: String): StoredCode? = codesByEmail[email]

	override fun consume(email: String) {
		codesByEmail.remove(email)
	}
}
