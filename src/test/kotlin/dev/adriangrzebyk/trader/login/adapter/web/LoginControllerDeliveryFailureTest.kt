package dev.adriangrzebyk.trader.login.adapter.web

import dev.adriangrzebyk.trader.login.OneTimeCodeDeliveryException
import dev.adriangrzebyk.trader.login.OneTimeCodeSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LoginControllerDeliveryFailureTest.FailingSenderConfiguration::class)
class LoginControllerDeliveryFailureTest {

	@TestConfiguration
	class FailingSenderConfiguration {

		@Bean
		@Primary
		fun failingSender(): OneTimeCodeSender = object : OneTimeCodeSender {
			override fun send(email: String, playerName: String, code: String) =
				throw OneTimeCodeDeliveryException(IllegalStateException("provider down"))
		}
	}

	@LocalServerPort
	private var port: Int = 0

	private lateinit var restTestClient: RestTestClient

	@BeforeEach
	fun setUp() {
		restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
	}

	@Test
	fun shouldTellThePlayerWhenTheCodeCouldNotBeSent() {
		restTestClient.post().uri("/api/login/request-code")
			.contentType(MediaType.APPLICATION_JSON)
			.body(LoginCodeRequestDto(playerName = "Adrian", corporationName = "Adrian Star Traders", email = "adrian@example.com"))
			.exchange()
			.expectStatus().isEqualTo(503)
			.expectBody(String::class.java).isEqualTo("We couldn't send the code. Try again.")
	}
}
