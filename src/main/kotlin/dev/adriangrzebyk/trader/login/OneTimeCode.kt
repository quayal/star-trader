package dev.adriangrzebyk.trader.login

import java.time.Duration

object OneTimeCode {

	val TTL: Duration = Duration.ofMinutes(10)
}
