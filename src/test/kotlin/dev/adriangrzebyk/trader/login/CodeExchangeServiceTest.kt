package dev.adriangrzebyk.trader.login

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CodeExchangeServiceTest {

	private val clock = FakeClock()
	private val codeStore = FakeOneTimeCodeStore()
	private val sessionStore = FakeSessionStore()
	private val service = CodeExchangeService(codeStore, sessionStore, FakeSessionTokenGenerator("session-token"), clock)

	private fun issueCode(email: String = "adrian@example.com", code: String = "483920") {
		codeStore.store(email, StoredCode(code, "Adrian", "Adrian Star Traders", clock.instant))
	}

	@Test
	fun shouldStartASessionForAValidUnexpiredCode() {
		issueCode()

		val session = service.exchangeCode("adrian@example.com", "483920")

		assertThat(session).isEqualTo(Session("session-token", "Adrian", "Adrian Star Traders", "adrian@example.com"))
		assertThat(sessionStore.find("session-token")).isEqualTo(session)
	}

	@Test
	fun shouldNotAcceptTheSameCodeTwice() {
		issueCode()
		service.exchangeCode("adrian@example.com", "483920")
		sessionStore.sessions.clear()

		assertThatThrownBy { service.exchangeCode("adrian@example.com", "483920") }
			.isInstanceOf(InvalidCodeExchangeException::class.java)
		assertThat(sessionStore.sessions).isEmpty()
	}

	@Test
	fun shouldRejectAWrongCodeWithoutStartingASession() {
		issueCode()

		assertThatThrownBy { service.exchangeCode("adrian@example.com", "000000") }
			.isInstanceOf(InvalidCodeExchangeException::class.java)
		assertThat(sessionStore.sessions).isEmpty()
	}

	@Test
	fun shouldKeepTheCodeUsableAfterAWrongAttempt() {
		issueCode()
		runCatching { service.exchangeCode("adrian@example.com", "000000") }

		assertThat(service.exchangeCode("adrian@example.com", "483920").token).isEqualTo("session-token")
	}

	@Test
	fun shouldRejectACodeOnceItsTimeToLiveHasPassed() {
		issueCode()
		clock.instant = clock.instant.plus(OneTimeCode.TTL)

		assertThatThrownBy { service.exchangeCode("adrian@example.com", "483920") }
			.isInstanceOf(InvalidCodeExchangeException::class.java)
		assertThat(sessionStore.sessions).isEmpty()
	}

	@Test
	fun shouldAcceptACodeJustBeforeItsTimeToLiveHasPassed() {
		issueCode()
		clock.instant = clock.instant.plus(OneTimeCode.TTL).minusSeconds(1)

		assertThat(service.exchangeCode("adrian@example.com", "483920").token).isEqualTo("session-token")
	}

	@Test
	fun shouldStartOnlyOneSessionWhenTwoExchangesRaceForTheSameCode() {
		issueCode()
		val racingStore = object : OneTimeCodeStore by codeStore {
			// The other exchange consumes the code between this one's read and its consume.
			override fun consume(email: String, code: StoredCode): Boolean {
				codeStore.consume(email, code)
				return codeStore.consume(email, code)
			}
		}
		val racingService = CodeExchangeService(racingStore, sessionStore, FakeSessionTokenGenerator("second-token"), clock)

		assertThatThrownBy { racingService.exchangeCode("adrian@example.com", "483920") }
			.isInstanceOf(InvalidCodeExchangeException::class.java)
		assertThat(sessionStore.sessions).isEmpty()
	}

	@Test
	fun shouldRejectAnEmailThatNeverRequestedACode() {
		assertThatThrownBy { service.exchangeCode("nobody@example.com", "483920") }
			.isInstanceOf(InvalidCodeExchangeException::class.java)
		assertThat(sessionStore.sessions).isEmpty()
	}

	@Test
	fun shouldRejectAnOlderCodeOnceANewerOneWasIssued() {
		issueCode(code = "111111")
		issueCode(code = "222222")

		assertThatThrownBy { service.exchangeCode("adrian@example.com", "111111") }
			.isInstanceOf(InvalidCodeExchangeException::class.java)
	}

	@Test
	fun shouldNormalizeTheEmailTheSameWayAsWhenRequestingTheCode() {
		issueCode()

		assertThat(service.exchangeCode(" Adrian@Example.com ", " 483920 ").email).isEqualTo("adrian@example.com")
	}
}
