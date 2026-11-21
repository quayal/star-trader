package dev.adriangrzebyk.trader.game.adapter.web

import java.security.MessageDigest

/**
 * The ids requests to join game [gameId] go by in a browser (R120): `r` and a hash of the game id and the requester's
 * email, so the email is never sent ([PlayerIds]). Unlike a place in a list, an id stays the same while the request
 * waits and is never reused for someone else, so the creator accepting a row that has just gone stale can't let in
 * another player.
 */
class JoinRequestIds(private val gameId: String) {

	fun of(email: String): String {
		val digest = MessageDigest.getInstance("SHA-256").digest("$gameId:$email".toByteArray())
		return "r" + digest.take(ID_BYTES).joinToString("") { "%02x".format(it) }
	}

	/** The email, among [emails], that [id] names; null when it names none of them. */
	fun emailOf(id: String, emails: List<String>): String? = emails.firstOrNull { of(it) == id }

	private companion object {
		const val ID_BYTES = 8
	}
}
