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
		store.store("adrian@example.com", "111111", now)
		store.store("adrian@example.com", "222222", now.plusSeconds(1))

		assertThat(store.codeFor("adrian@example.com")).isEqualTo(StoredCode("222222", now.plusSeconds(1)))
	}

	@Test
	fun shouldPurgeExpiredCodesWhenStoringANewOne() {
		store.store("old@example.com", "111111", now)

		store.store("new@example.com", "222222", now.plus(OneTimeCode.TTL).plusSeconds(1))

		assertThat(store.codeFor("old@example.com")).isNull()
	}

	@Test
	fun shouldForgetAConsumedCode() {
		store.store("adrian@example.com", "111111", now)

		store.consume("adrian@example.com")

		assertThat(store.codeFor("adrian@example.com")).isNull()
	}
}
