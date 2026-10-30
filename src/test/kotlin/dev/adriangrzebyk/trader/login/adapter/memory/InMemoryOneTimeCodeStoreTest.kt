package dev.adriangrzebyk.trader.login.adapter.memory

import dev.adriangrzebyk.trader.login.OneTimeCode
import dev.adriangrzebyk.trader.login.StoredCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class InMemoryOneTimeCodeStoreTest {

	private val store = InMemoryOneTimeCodeStore()
	private val now = Instant.parse("2026-10-29T12:00:00Z")

	@Test
	fun shouldReturnTheLatestCodeForAnEmail() {
		store.store("adrian@example.com", code("111111", now))
		store.store("adrian@example.com", code("222222", now.plusSeconds(1)))

		assertThat(store.codeFor("adrian@example.com")).isEqualTo(code("222222", now.plusSeconds(1)))
	}

	@Test
	fun shouldPurgeExpiredCodesWhenStoringANewOne() {
		store.store("old@example.com", code("111111", now))

		store.store("new@example.com", code("222222", now.plus(OneTimeCode.TTL).plusSeconds(1)))

		assertThat(store.codeFor("old@example.com")).isNull()
	}

	@Test
	fun shouldForgetAConsumedCode() {
		store.store("adrian@example.com", code("111111", now))

		assertThat(store.consume("adrian@example.com", code("111111", now))).isTrue()
		assertThat(store.codeFor("adrian@example.com")).isNull()
	}

	@Test
	fun shouldReplaceOnlyTheExpectedCode() {
		store.store("adrian@example.com", code("111111", now))
		val counted = code("111111", now).copy(attempts = 1)

		assertThat(store.replace("adrian@example.com", code("111111", now), counted)).isTrue()
		assertThat(store.replace("adrian@example.com", code("111111", now), counted.copy(attempts = 2))).isFalse()
		assertThat(store.codeFor("adrian@example.com")).isEqualTo(counted)
	}

	@Test
	fun shouldNotConsumeACodeThatIsNoLongerTheStoredOne() {
		store.store("adrian@example.com", code("111111", now))
		store.store("adrian@example.com", code("222222", now))

		assertThat(store.consume("adrian@example.com", code("111111", now))).isFalse()
		assertThat(store.codeFor("adrian@example.com")).isEqualTo(code("222222", now))
	}

	private fun code(code: String, issuedAt: Instant) = StoredCode(code, "Adrian", "Adrian Star Traders", issuedAt)
}
