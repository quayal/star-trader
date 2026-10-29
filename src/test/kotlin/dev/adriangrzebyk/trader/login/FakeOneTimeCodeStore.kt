package dev.adriangrzebyk.trader.login

class FakeOneTimeCodeStore : OneTimeCodeStore {

	private val codesByEmail = mutableMapOf<String, StoredCode>()

	override fun store(email: String, code: StoredCode) {
		codesByEmail[email] = code
	}

	override fun codeFor(email: String): StoredCode? = codesByEmail[email]

	override fun replace(email: String, expected: StoredCode, updated: StoredCode): Boolean = codesByEmail.replace(email, expected, updated)

	override fun consume(email: String, code: StoredCode): Boolean = codesByEmail.remove(email, code)

	fun storedCodeFor(email: String): String? = codesByEmail[email]?.code
}
