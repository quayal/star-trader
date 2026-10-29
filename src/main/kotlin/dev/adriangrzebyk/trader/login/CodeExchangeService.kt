package dev.adriangrzebyk.trader.login

import java.security.MessageDigest

class CodeExchangeService(
	private val codeStore: OneTimeCodeStore,
	private val sessionStore: SessionStore,
	private val tokenGenerator: SessionTokenGenerator,
	private val clock: Clock,
) {

	/** Every failure throws the same exception, so a caller can't tell whether an email ever requested a code. */
	fun exchangeCode(email: String, code: String): Session {
		val normalizedEmail = email.trim().lowercase()
		val stored = codeStore.codeFor(normalizedEmail) ?: throw InvalidCodeExchangeException()
		if (!matches(stored.code, code.trim()) || isExpired(stored) || !codeStore.consume(normalizedEmail, stored)) {
			throw InvalidCodeExchangeException()
		}

		val session = Session(tokenGenerator.generate(), stored.playerName, stored.corporationName, normalizedEmail)
		sessionStore.store(session)
		return session
	}

	private fun isExpired(stored: StoredCode): Boolean = !clock.now().isBefore(stored.issuedAt.plus(OneTimeCode.TTL))

	private fun matches(expected: String, actual: String): Boolean = MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
}
