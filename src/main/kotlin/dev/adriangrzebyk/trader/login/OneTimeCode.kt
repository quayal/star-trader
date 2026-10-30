package dev.adriangrzebyk.trader.login

import java.time.Duration

object OneTimeCode {

	val TTL: Duration = Duration.ofMinutes(10)

	/** Guesses allowed per code, so a 6-digit code can't be brute-forced within its TTL. */
	const val MAX_ATTEMPTS = 5
}
