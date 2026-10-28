package dev.adriangrzebyk.trader.login.adapter.memory

import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryOneTimeCodeStore(private val clock: Clock = Clock.systemUTC()) : OneTimeCodeStore {

	private data class IssuedCode(val code: String, val expiresAt: Instant)

	private val codesByEmail = ConcurrentHashMap<String, IssuedCode>()

	override fun store(email: String, code: String) {
		val now = Instant.now(clock)
		codesByEmail.values.removeIf { it.expiresAt.isBefore(now) }
		codesByEmail[email] = IssuedCode(code, now.plus(CODE_TTL))
	}

	companion object {
		val CODE_TTL: Duration = Duration.ofMinutes(10)
	}
}
