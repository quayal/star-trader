package dev.adriangrzebyk.trader.login

class FakeSessionTokenGenerator(private val token: String) : SessionTokenGenerator {

	override fun generate(): String = token
}
