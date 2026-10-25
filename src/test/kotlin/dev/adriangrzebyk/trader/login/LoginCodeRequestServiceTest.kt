package dev.adriangrzebyk.trader.login

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LoginCodeRequestServiceTest {

	private val codeStore = FakeOneTimeCodeStore()
	private val codeSender = FakeOneTimeCodeSender()
	private val service = LoginCodeRequestService(
		codeGenerator = FakeOneTimeCodeGenerator("483920"),
		codeSender = codeSender,
		codeStore = codeStore,
	)

	@Test
	fun shouldStoreAndSendACodeForAValidRequest() {
		val request = LoginCodeRequest(playerName = "Adrian", corporationName = "Adrian Star Traders", email = "adrian@example.com")

		service.requestCode(request)

		assertThat(codeStore.codeFor("adrian@example.com")).isEqualTo("483920")
		assertThat(codeSender.sent).containsExactly(
			FakeOneTimeCodeSender.SentCode(email = "adrian@example.com", playerName = "Adrian", code = "483920"),
		)
	}

	@Test
	fun shouldRejectAMalformedEmailWithoutStoringOrSendingACode() {
		val request = LoginCodeRequest(playerName = "Adrian", corporationName = "Adrian Star Traders", email = "not-an-email")

		assertThatThrownBy { service.requestCode(request) }
			.isInstanceOf(InvalidLoginRequestException::class.java)

		assertThat(codeStore.codeFor("not-an-email")).isNull()
		assertThat(codeSender.sent).isEmpty()
	}

	@Test
	fun shouldOverwriteAPreviouslyIssuedCodeForTheSameEmailOnARepeatRequest() {
		val firstService = LoginCodeRequestService(FakeOneTimeCodeGenerator("111111"), codeSender, codeStore)
		val secondService = LoginCodeRequestService(FakeOneTimeCodeGenerator("222222"), codeSender, codeStore)
		val request = LoginCodeRequest(playerName = "Adrian", corporationName = "Adrian Star Traders", email = "adrian@example.com")

		firstService.requestCode(request)
		secondService.requestCode(request)

		assertThat(codeStore.codeFor("adrian@example.com")).isEqualTo("222222")
	}
}
