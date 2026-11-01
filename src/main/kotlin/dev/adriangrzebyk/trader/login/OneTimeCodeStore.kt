package dev.adriangrzebyk.trader.login

import java.time.Instant

interface OneTimeCodeStore {

	fun store(email: String, code: StoredCode)

	fun codeFor(email: String): StoredCode?

	/** Swaps [expected] for [updated] only if [expected] is still the one stored for [email]. */
	fun replace(email: String, expected: StoredCode, updated: StoredCode): Boolean

	/** Removes [code] only if it is still the one stored for [email]; false when another exchange got there first. */
	fun consume(email: String, code: StoredCode): Boolean
}

/** A code waiting to be exchanged, with the identity the player gave when requesting it. */
data class StoredCode(val code: String, val playerName: String, val corporationName: String, val issuedAt: Instant, val attempts: Int = 0)
