package dev.adriangrzebyk.trader.login.adapter.email

import dev.adriangrzebyk.trader.StarTraderApplication
import dev.adriangrzebyk.trader.login.OneTimeCodeSender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

/**
 * Starts the real application context to check that `RESEND_API_KEY`, `RESEND_FROM_ADDRESS` and
 * the `prod` profile reach [ResendOneTimeCodeSender]. The variables are passed as command-line
 * arguments, which take precedence over the developer's real environment.
 */
class ResendOneTimeCodeSenderWiringTest {

	private fun start(vararg args: String, prod: Boolean = false): ConfigurableApplicationContext =
		SpringApplicationBuilder(StarTraderApplication::class.java)
			.web(WebApplicationType.NONE)
			.profiles(*if (prod) arrayOf("prod") else emptyArray())
			.run(*args)

	@Test
	fun shouldInjectTheResendSenderAndStartWithoutConfigurationOutsideProd() {
		start("--RESEND_API_KEY=", "--RESEND_FROM_ADDRESS=").use { context ->
			assertThat(context.getBean(OneTimeCodeSender::class.java)).isInstanceOf(ResendOneTimeCodeSender::class.java)
		}
	}

	@Test
	fun shouldRefuseToStartUnderProdWithoutTheEnvironmentVariables() {
		assertThatThrownBy { start("--RESEND_API_KEY=", "--RESEND_FROM_ADDRESS=", prod = true) }
			.rootCause()
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("RESEND_API_KEY")
	}

	@Test
	fun shouldRefuseToStartUnderProdWithoutTheSenderAddress() {
		assertThatThrownBy { start("--RESEND_API_KEY=re_test_key", "--RESEND_FROM_ADDRESS=", prod = true) }
			.rootCause()
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("RESEND_FROM_ADDRESS")
	}

	@Test
	fun shouldStartUnderProdWhenBothEnvironmentVariablesAreSet() {
		start("--RESEND_API_KEY=re_test_key", "--RESEND_FROM_ADDRESS=login@star-trader.example", prod = true).use { context ->
			assertThat(context.getBean(OneTimeCodeSender::class.java)).isInstanceOf(ResendOneTimeCodeSender::class.java)
		}
	}
}
