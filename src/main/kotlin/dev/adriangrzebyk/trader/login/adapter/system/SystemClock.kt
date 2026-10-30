package dev.adriangrzebyk.trader.login.adapter.system

import dev.adriangrzebyk.trader.login.Clock
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SystemClock : Clock {

	override fun now(): Instant = Instant.now()
}
