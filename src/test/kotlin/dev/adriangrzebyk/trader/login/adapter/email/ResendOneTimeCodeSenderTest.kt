package dev.adriangrzebyk.trader.login.adapter.email

import dev.adriangrzebyk.trader.login.OneTimeCodeDeliveryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

@ExtendWith(OutputCaptureExtension::class)
class ResendOneTimeCodeSenderTest {

	private val restClientBuilder = RestClient.builder()
	private val server = MockRestServiceServer.bindTo(restClientBuilder).build()

	private fun sender(apiKey: String = "re_test_key", from: String = "login@star-trader.example", production: Boolean = false) =
		ResendOneTimeCodeSender(apiKey, from, production, restClientBuilder)

	@Test
	fun shouldPostTheCodeToResend() {
		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer re_test_key"))
			.andExpect(
				content().json(
					"""
					{
					  "from": "login@star-trader.example",
					  "to": ["adrian@example.com"],
					  "subject": "Your Star Trader sign-in code"
					}
					""",
				),
			)
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Your Star Trader sign-in code is 483920.")))
			.andRespond(withSuccess("""{"id":"email-id"}""", MediaType.APPLICATION_JSON))

		sender().send("adrian@example.com", "Adrian", "483920")

		server.verify()
	}

	@Test
	fun shouldFailTheSendWhenResendRejectsIt(output: CapturedOutput) {
		server.expect(requestTo("https://api.resend.com/emails"))
			.andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("""{"message":"invalid from"}"""))

		assertThatThrownBy { sender().send("adrian@example.com", "Adrian", "483920") }
			.isInstanceOf(OneTimeCodeDeliveryException::class.java)
		assertThat(output).doesNotContain("re_test_key").doesNotContain("483920")
	}

	@Test
	fun shouldLogTheCodeWithoutCallingResendWhenNotConfigured(output: CapturedOutput) {
		sender(apiKey = "").send("adrian@example.com", "Adrian", "483920")
		sender(from = " ").send("adrian@example.com", "Adrian", "483920")

		server.verify()
		assertThat(output).contains("One-time sign-in code for Adrian <adrian@example.com>: 483920")
	}

	@Test
	fun shouldRefuseToStartUnderProdWithoutConfiguration() {
		assertThatThrownBy { sender(apiKey = "", production = true) }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("RESEND_API_KEY")
	}

	@Test
	fun shouldStartUnderProdWhenConfigured() {
		sender(production = true)
	}
}
