package dev.adriangrzebyk.trader.login

interface OneTimeCodeStore {

	fun store(email: String, code: String)
}
