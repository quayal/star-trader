package dev.adriangrzebyk.trader.login

interface OneTimeCodeSender {

	fun send(email: String, playerName: String, code: String)
}
