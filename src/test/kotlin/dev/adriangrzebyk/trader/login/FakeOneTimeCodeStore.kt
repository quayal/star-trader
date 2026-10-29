package dev.adriangrzebyk.trader.login

import java.time.Instant

class FakeOneTimeCodeStore : OneTimeCodeStore {

	private val codesByEmail = mutableMapOf<String, StoredCode>()

	override fun store(email: String, code: String, issuedAt: Instant) {
		codesByEmail[email] = StoredCode(code, issuedAt)
	}

	override fun codeFor(email: String): StoredCode? = codesByEmail[email]

	override fun consume(email: String) {
		codesByEmail.remove(email)
	}

	fun storedCodeFor(email: String): String? = codesByEmail[email]?.code
}
