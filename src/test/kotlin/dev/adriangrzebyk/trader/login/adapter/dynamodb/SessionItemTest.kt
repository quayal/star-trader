package dev.adriangrzebyk.trader.login.adapter.dynamodb

import dev.adriangrzebyk.trader.login.Session
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionItemTest {

	private val session = Session("3f1c2a9e-token", "Adrian", "Adrian Star Traders", "adrian@example.com")

	@Test
	fun shouldReadBackTheSessionItWrote() {
		val stored = SessionItem(SessionItem(session).attributes)

		assertThat(stored.toSession(session.token)).isEqualTo(session)
	}

	@Test
	fun shouldKeyTheItemByAHashOfTheTokenAndNeverStoreTheTokenItself() {
		val attributes = SessionItem(session).attributes

		assertThat(attributes.getValue(SessionItem.ID).s()).matches("session#[0-9a-f]{64}")
		assertThat(attributes.values.map { it.s() }).noneMatch { it.contains(session.token) }
	}

	@Test
	fun shouldGiveDifferentTokensDifferentIds() {
		assertThat(sessionItemId("token-a")).isNotEqualTo(sessionItemId("token-b"))
		assertThat(sessionItemId("token-a")).isEqualTo(sessionItemId("token-a"))
	}
}
