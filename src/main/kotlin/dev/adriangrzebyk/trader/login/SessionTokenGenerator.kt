package dev.adriangrzebyk.trader.login

interface SessionTokenGenerator {

	fun generate(): String
}
