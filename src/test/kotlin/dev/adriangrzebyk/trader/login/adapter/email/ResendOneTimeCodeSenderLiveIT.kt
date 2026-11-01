package dev.adriangrzebyk.trader.login.adapter.email

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.web.client.RestClient

/**
 * Sends a real code through Resend: the end-to-end check for brief assumption A02. Skipped unless
 * RESEND_API_KEY is set; also needs RESEND_FROM_ADDRESS (on a domain Resend shows as verified) and
 * RESEND_TEST_RECIPIENT_EMAIL. Run with `./gradlew test --tests "*ResendOneTimeCodeSenderLiveIT*"`
 * and check that the inbox received the code.
 */
@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")
class ResendOneTimeCodeSenderLiveIT {

	@Test
	fun shouldDeliverARealCode() {
		val sender = ResendOneTimeCodeSender(
			apiKey = System.getenv("RESEND_API_KEY"),
			from = requireNotNull(System.getenv("RESEND_FROM_ADDRESS")) { "Set RESEND_FROM_ADDRESS" },
			production = true,
			restClientBuilder = RestClient.builder()
		)

		sender.send(requireNotNull(System.getenv("RESEND_TEST_RECIPIENT_EMAIL")) { "Set RESEND_TEST_RECIPIENT_EMAIL" }, "Live test", "123456")
	}
}
