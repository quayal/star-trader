package dev.adriangrzebyk.trader.login

class CodeExchangeService(
	private val codeStore: OneTimeCodeStore,
	private val sessionStore: SessionStore,
	private val tokenGenerator: SessionTokenGenerator,
	private val clock: Clock
) {

	/** Every failure throws the same exception, so a caller can't tell whether an email ever requested a code. */
	fun exchangeCode(email: String, code: String): Session {
		val normalizedEmail = email.trim().lowercase()
		val attempt = reserveAttempt(normalizedEmail)

		if (!attempt.matches(code.trim())) {
			if (attempt.attemptsUsedUp()) {
				codeStore.consume(normalizedEmail, attempt)
			}
			throw InvalidCodeExchangeException()
		}
		if (!codeStore.consume(normalizedEmail, attempt)) {
			throw InvalidCodeExchangeException()
		}

		val session = Session(tokenGenerator.generate(), attempt.playerName, attempt.corporationName, normalizedEmail)
		sessionStore.store(session)
		return session
	}

	/**
	 * Counts this attempt before the code is compared, so parallel guesses can't all be checked
	 * against the same snapshot: each code is compared at most [OneTimeCode.MAX_ATTEMPTS] times.
	 */
	private fun reserveAttempt(email: String): StoredCode {
		while (true) {
			val stored = codeStore.codeFor(email) ?: throw InvalidCodeExchangeException()
			if (!stored.canBeTried(clock.now())) {
				throw InvalidCodeExchangeException()
			}
			val reserved = stored.withAttempt()
			if (codeStore.replace(email, stored, reserved)) {
				return reserved
			}
		}
	}
}
