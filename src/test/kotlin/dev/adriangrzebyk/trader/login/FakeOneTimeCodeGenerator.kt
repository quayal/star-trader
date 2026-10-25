package dev.adriangrzebyk.trader.login

class FakeOneTimeCodeGenerator(private val code: String) : OneTimeCodeGenerator {

	override fun generate(): String = code
}
