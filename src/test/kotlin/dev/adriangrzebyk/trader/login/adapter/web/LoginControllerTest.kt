package dev.adriangrzebyk.trader.login.adapter.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

// Blank the Resend settings so a developer's RESEND_* variables don't send real email to example.com.
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["RESEND_API_KEY=", "RESEND_FROM_ADDRESS="],
)
class LoginControllerTest {

	@LocalServerPort
	private var port: Int = 0

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
			email = "adrian@example.com",
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
			email = "not-an-email",
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
			email = "adrian@example.com",
		)

		restTestClient.post().uri("/api/login/request-code")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.exchange()
			.expectStatus().isBadRequest
	}
}
