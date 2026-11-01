package dev.adriangrzebyk.trader.login.adapter.web

import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

// Blank the Resend settings so a developer's RESEND_* variables don't send real email to example.com.
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["RESEND_API_KEY=", "RESEND_FROM_ADDRESS="]
)
class LoginControllerTest {

	@LocalServerPort
	private var port: Int = 0

	@Autowired
	private lateinit var codeStore: OneTimeCodeStore

	private lateinit var restTestClient: RestTestClient

	@BeforeEach
	fun setUp() {
		restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
	}

	@Test
	fun shouldAcceptAValidLoginCodeRequest() {
		val request = LoginCodeRequestDto(
			playerName = "Adrian",
			corporationName = "Adrian Star Traders",
			email = "adrian@example.com"
		)

		restTestClient.post().uri("/api/login/request-code")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.exchange()
			.expectStatus().isEqualTo(202)
	}

	@Test
	fun shouldRejectAMalformedEmail() {
		val request = LoginCodeRequestDto(
			playerName = "Adrian",
			corporationName = "Adrian Star Traders",
			email = "not-an-email"
		)

		restTestClient.post().uri("/api/login/request-code")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.exchange()
			.expectStatus().isBadRequest
	}

	@Test
	fun shouldRejectABlankPlayerName() {
		val request = LoginCodeRequestDto(
			playerName = " ",
			corporationName = "Adrian Star Traders",
			email = "adrian@example.com"
		)

		restTestClient.post().uri("/api/login/request-code")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.exchange()
			.expectStatus().isBadRequest
	}

	@Test
	fun shouldExchangeARequestedCodeForASessionThatIdentifiesThePlayer() {
		val email = "exchange@example.com"
		val code = requestCode(email)

		val token = exchange(email, code)
			.expectStatus().isOk
			.expectBody(SessionTokenResponseDto::class.java).returnResult().responseBody!!.sessionToken

		assertThat(token).isNotBlank()
		restTestClient.get().uri("/api/login/session")
			.header("Authorization", "Bearer $token")
			.exchange()
			.expectStatus().isOk
			.expectBody(SessionResponseDto::class.java)
			.isEqualTo(SessionResponseDto("Adrian", "Adrian Star Traders", email))
	}

	@Test
	fun shouldRejectAWrongCode() {
		val email = "wrong-code@example.com"
		val code = requestCode(email)

		exchange(email, if (code == "000000") "111111" else "000000").expectStatus().isUnauthorized
	}

	@Test
	fun shouldRejectTheSameCodeASecondTime() {
		val email = "reuse@example.com"
		val code = requestCode(email)

		exchange(email, code).expectStatus().isOk
		exchange(email, code).expectStatus().isUnauthorized
	}

	@Test
	fun shouldRejectTheRightCodeAfterTooManyWrongGuesses() {
		val email = "brute-force@example.com"
		val code = requestCode(email)
		val wrong = if (code == "000000") "111111" else "000000"

		repeat(5) { exchange(email, wrong).expectStatus().isUnauthorized }
		exchange(email, code).expectStatus().isUnauthorized
	}

	@Test
	fun shouldRejectAnExchangeWithoutACode() {
		restTestClient.post().uri("/api/login/exchange-code")
			.contentType(MediaType.APPLICATION_JSON)
			.body(ExchangeCodeRequestDto(email = "adrian@example.com", code = " "))
			.exchange()
			.expectStatus().isBadRequest
	}

	@Test
	fun shouldRejectASessionReadWithAnUnknownOrMissingToken() {
		restTestClient.get().uri("/api/login/session")
			.header("Authorization", "Bearer not-a-real-token")
			.exchange()
			.expectStatus().isUnauthorized
		restTestClient.get().uri("/api/login/session")
			.exchange()
			.expectStatus().isUnauthorized
		restTestClient.get().uri("/api/login/session")
			.header("Authorization", "Basic abc")
			.exchange()
			.expectStatus().isUnauthorized
	}

	private fun requestCode(email: String): String {
		restTestClient.post().uri("/api/login/request-code")
			.contentType(MediaType.APPLICATION_JSON)
			.body(LoginCodeRequestDto(playerName = "Adrian", corporationName = "Adrian Star Traders", email = email))
			.exchange()
			.expectStatus().isEqualTo(202)
		return codeStore.codeFor(email)!!.code
	}

	private fun exchange(email: String, code: String) = restTestClient.post().uri("/api/login/exchange-code")
		.contentType(MediaType.APPLICATION_JSON)
		.body(ExchangeCodeRequestDto(email = email, code = code))
		.exchange()
}
