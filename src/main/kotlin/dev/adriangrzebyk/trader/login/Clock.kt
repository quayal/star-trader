package dev.adriangrzebyk.trader.login

import java.time.Instant

interface Clock {

	fun now(): Instant
}
