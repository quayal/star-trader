package dev.adriangrzebyk.trader.login

class FakeSessionStore : SessionStore {

	val sessions = mutableMapOf<String, Session>()

	override fun store(session: Session) {
		sessions[session.token] = session
	}

	override fun find(token: String): Session? = sessions[token]
}
