package dev.adriangrzebyk.trader.login

import java.time.Instant

class FakeClock(var instant: Instant = Instant.parse("2026-10-29T12:00:00Z")) : Clock {

	override fun now(): Instant = instant
}
