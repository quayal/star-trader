package dev.adriangrzebyk.trader.login.adapter.memory

import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemorySessionStore : SessionStore {

	private val sessionsByToken = ConcurrentHashMap<String, Session>()

	override fun store(session: Session) {
		sessionsByToken[session.token] = session
	}

	override fun find(token: String): Session? = sessionsByToken[token]
}
