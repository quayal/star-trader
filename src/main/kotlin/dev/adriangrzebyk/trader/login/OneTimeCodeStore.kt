package dev.adriangrzebyk.trader.login

import java.time.Instant

interface OneTimeCodeStore {

	fun store(email: String, code: String, issuedAt: Instant)

	fun codeFor(email: String): StoredCode?

	fun consume(email: String)
}

data class StoredCode(val code: String, val issuedAt: Instant)
