package dev.adriangrzebyk.trader.login.adapter.email

import dev.adriangrzebyk.trader.login.OneTimeCodeDeliveryException
import dev.adriangrzebyk.trader.login.OneTimeCodeSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

/**
 * Emails the one-time code through Resend (https://resend.com).
 *
 * Without an API key and sender address (local dev, CI), the code is logged instead, as the
 * placeholder sender did. Under the `prod` profile that fallback would leak live codes into the
 * log, so a missing configuration stops the application from starting.
 */
@Component
class ResendOneTimeCodeSender internal constructor(
	private val apiKey: String,
	private val from: String,
	production: Boolean,
	restClientBuilder: RestClient.Builder,
) : OneTimeCodeSender {

	@Autowired
	constructor(
		@Value($$"${star-trader.email.resend.api-key:}") apiKey: String,
		@Value($$"${star-trader.email.resend.from:}") from: String,
		@Value("#{environment.matchesProfiles('prod')}") production: Boolean,
	) : this(apiKey, from, production, RestClient.builder().requestFactory(timeoutRequestFactory()))

	private val log = LoggerFactory.getLogger(ResendOneTimeCodeSender::class.java)

	private val configured = apiKey.isNotBlank() && from.isNotBlank()

	private val restClient = restClientBuilder.baseUrl(RESEND_API_URL).build()

	init {
		check(configured || !production) {
			"RESEND_API_KEY and RESEND_FROM_ADDRESS must be set under the prod profile"
		}
	}

	override fun send(email: String, playerName: String, code: String) {
		if (!configured) {
			log.info("One-time sign-in code for {} <{}>: {}", playerName, email, code)
			return
		}

		try {
			restClient.post().uri("/emails")
				.header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
				.contentType(MediaType.APPLICATION_JSON)
				.body(ResendEmail(from = from, to = listOf(email), subject = SUBJECT, text = textFor(playerName, code)))
				.retrieve()
				.toBodilessEntity()
		} catch (ex: RestClientException) {
			// The exception message can echo the response body, so log only its type.
			log.error("Resend did not accept the one-time code email ({})", ex.javaClass.simpleName)
			throw OneTimeCodeDeliveryException(ex)
		}
	}

	private fun textFor(playerName: String, code: String): String = """
		|Hi $playerName,
		|
		|Your Star Trader sign-in code is $code.
		|
		|If you didn't ask to sign in, you can ignore this email.
	""".trimMargin()

	private data class ResendEmail(val from: String, val to: List<String>, val subject: String, val text: String)

	companion object {
		private const val RESEND_API_URL = "https://api.resend.com"
		private const val SUBJECT = "Your Star Trader sign-in code"

		/** A hung Resend call must not hold the request-code thread forever. */
		private fun timeoutRequestFactory() = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(Duration.ofSeconds(5))
			setReadTimeout(Duration.ofSeconds(10))
		}
	}
}
