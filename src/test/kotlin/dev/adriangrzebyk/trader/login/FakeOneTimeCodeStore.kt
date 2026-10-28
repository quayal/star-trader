package dev.adriangrzebyk.trader.login

class FakeOneTimeCodeStore : OneTimeCodeStore {

	private val codesByEmail = mutableMapOf<String, String>()

	override fun store(email: String, code: String) {
		codesByEmail[email] = code
	}

	fun storedCodeFor(email: String): String? = codesByEmail[email]
}
