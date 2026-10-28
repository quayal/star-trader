package dev.adriangrzebyk.trader.login.adapter.email

import dev.adriangrzebyk.trader.login.OneTimeCodeSender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Placeholder adapter pending E01-S01-T01 (#13): logs the one-time code instead of
 * emailing it. Swap for a real transactional-email adapter behind [OneTimeCodeSender]
 * once a provider is chosen — no domain change needed. Disabled under the `prod` profile
 * so live codes are never logged in production.
 */
@Component
@Profile("!prod")
class LoggingOneTimeCodeSender : OneTimeCodeSender {

	private val log = LoggerFactory.getLogger(LoggingOneTimeCodeSender::class.java)

	override fun send(email: String, playerName: String, code: String) {
		log.info("One-time sign-in code for {} <{}>: {}", playerName, email, code)
	}
}
