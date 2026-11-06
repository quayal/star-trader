package dev.adriangrzebyk.trader.login

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class StoredCodeTest {

	private val issuedAt = Instant.parse("2026-11-06T10:00:00Z")
	private val code = StoredCode("123456", "Ada", "Orion Freight", issuedAt, 0)

	@Test
	fun shouldBeTriableUntilItExpires() {
		assertThat(code.canBeTried(issuedAt.plus(OneTimeCode.TTL).minusSeconds(1))).isTrue()
		assertThat(code.canBeTried(issuedAt.plus(OneTimeCode.TTL))).isFalse()
	}

	@Test
	fun shouldNotBeTriableOnceItsAttemptsAreUsedUp() {
		val tried = (1..OneTimeCode.MAX_ATTEMPTS).fold(code) { stored, _ -> stored.withAttempt() }

		assertThat(tried.attemptsUsedUp()).isTrue()
		assertThat(tried.canBeTried(issuedAt)).isFalse()
	}

	@Test
	fun shouldMatchOnlyItsOwnCode() {
		assertThat(code.matches("123456")).isTrue()
		assertThat(code.matches("123457")).isFalse()
	}
}
