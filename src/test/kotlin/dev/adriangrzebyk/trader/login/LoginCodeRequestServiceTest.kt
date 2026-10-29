package dev.adriangrzebyk.trader.login

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LoginCodeRequestServiceTest {

	private val codeStore = FakeOneTimeCodeStore()
	private val codeSender = FakeOneTimeCodeSender()
	private val clock = FakeClock()
	private val service = LoginCodeRequestService(
		codeGenerator = FakeOneTimeCodeGenerator("483920"),
		codeSender = codeSender,
		codeStore = codeStore,
		clock = clock,
	)

	@Test
	fun shouldStoreAndSendACodeForAValidRequest() {
		val request = LoginCodeRequest(playerName = "Adrian", corporationName = "Adrian Star Traders", email = "adrian@example.com")

		service.requestCode(request)

		assertThat(codeStore.codeFor("adrian@example.com")).isEqualTo(StoredCode("483920", clock.instant))
		assertThat(codeSender.sent).containsExactly(
			FakeOneTimeCodeSender.SentCode(email = "adrian@example.com", playerName = "Adrian", code = "483920"),
		)
	}

	@Test
	fun shouldRejectAMalformedEmailWithoutStoringOrSendingACode() {
		val request = LoginCodeRequest(playerName = "Adrian", corporationName = "Adrian Star Traders", email = "not-an-email")

		assertThatThrownBy { service.requestCode(request) }
			.isInstanceOf(InvalidLoginRequestException::class.java)

		assertThat(codeStore.storedCodeFor("not-an-email")).isNull()
		assertThat(codeSender.sent).isEmpty()
	}

	@Test
	fun shouldOverwriteAPreviouslyIssuedCodeForTheSameEmailOnARepeatRequest() {
		val firstService = LoginCodeRequestService(FakeOneTimeCodeGenerator("111111"), codeSender, codeStore, clock)
		val secondService = LoginCodeRequestService(FakeOneTimeCodeGenerator("222222"), codeSender, codeStore, clock)
		val request = LoginCodeRequest(playerName = "Adrian", corporationName = "Adrian Star Traders", email = "adrian@example.com")

		firstService.requestCode(request)
		secondService.requestCode(request)

		assertThat(codeStore.storedCodeFor("adrian@example.com")).isEqualTo("222222")
	}

	@Test
	fun shouldNormalizeTheEmailBeforeStoringAndSending() {
		service.requestCode(LoginCodeRequest("Adrian", "Adrian Star Traders", " Adrian@Example.com "))

		assertThat(codeStore.storedCodeFor("adrian@example.com")).isEqualTo("483920")
		assertThat(codeSender.sent.single().email).isEqualTo("adrian@example.com")
	}

	@Test
	fun shouldRejectAnEmailContainingWhitespaceOrLineBreaks() {
		listOf("a b@c.com", "victim@x.com\nINFO forged", "a@b.com\r\nBcc: x@y.com").forEach {
			assertThatThrownBy { service.requestCode(LoginCodeRequest("Adrian", "Adrian Star Traders", it)) }
				.isInstanceOf(InvalidLoginRequestException::class.java)
		}
		assertThat(codeSender.sent).isEmpty()
	}

	@Test
	fun shouldRejectBlankNames() {
		assertThatThrownBy { service.requestCode(LoginCodeRequest(" ", "Adrian Star Traders", "adrian@example.com")) }
			.isInstanceOf(InvalidLoginRequestException::class.java)
		assertThatThrownBy { service.requestCode(LoginCodeRequest("Adrian", " ", "adrian@example.com")) }
			.isInstanceOf(InvalidLoginRequestException::class.java)
	}

	@Test
	fun shouldKeepThePreviousCodeWhenSendingFails() {
		val failingSender = object : OneTimeCodeSender {
			override fun send(email: String, playerName: String, code: String) = throw IllegalStateException("provider down")
		}
		service.requestCode(LoginCodeRequest("Adrian", "Adrian Star Traders", "adrian@example.com"))

		assertThatThrownBy {
			LoginCodeRequestService(FakeOneTimeCodeGenerator("999999"), failingSender, codeStore, clock)
				.requestCode(LoginCodeRequest("Adrian", "Adrian Star Traders", "adrian@example.com"))
		}.isInstanceOf(IllegalStateException::class.java)

		assertThat(codeStore.storedCodeFor("adrian@example.com")).isEqualTo("483920")
	}
}
