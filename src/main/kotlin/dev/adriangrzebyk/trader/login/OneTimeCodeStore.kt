package dev.adriangrzebyk.trader.login

import java.security.MessageDigest
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
data class StoredCode(val code: String, val playerName: String, val corporationName: String, val issuedAt: Instant, val attempts: Int = 0) {

	/** Whether this code may still be tried at [now]: not expired, and not out of attempts. */
	fun canBeTried(now: Instant): Boolean = now.isBefore(issuedAt.plus(OneTimeCode.TTL)) && !attemptsUsedUp()

	/** Whether this code has been tried [OneTimeCode.MAX_ATTEMPTS] times, so it can't be tried again. */
	fun attemptsUsedUp(): Boolean = attempts >= OneTimeCode.MAX_ATTEMPTS

	/** This code with one more attempt counted. */
	fun withAttempt(): StoredCode = copy(attempts = attempts + 1)

	/** Whether [guess] is this code, compared in constant time so the comparison doesn't leak how much of it matched. */
	fun matches(guess: String): Boolean = MessageDigest.isEqual(code.toByteArray(), guess.toByteArray())
}
