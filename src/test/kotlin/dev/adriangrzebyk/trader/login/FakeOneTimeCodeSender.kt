package dev.adriangrzebyk.trader.login

class FakeOneTimeCodeSender : OneTimeCodeSender {

	data class SentCode(val email: String, val playerName: String, val code: String)

	val sent = mutableListOf<SentCode>()

	override fun send(email: String, playerName: String, code: String) {
		sent.add(SentCode(email, playerName, code))
	}
}
