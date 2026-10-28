package dev.adriangrzebyk.trader.login

interface OneTimeCodeGenerator {

	fun generate(): String
}
