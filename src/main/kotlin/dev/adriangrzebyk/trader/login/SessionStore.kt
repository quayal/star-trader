package dev.adriangrzebyk.trader.login

interface SessionStore {

	fun store(session: Session)

	fun find(token: String): Session?
}
