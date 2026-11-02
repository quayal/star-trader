package dev.adriangrzebyk.trader.login.adapter.dynamodb

import dev.adriangrzebyk.trader.login.Session
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.security.MessageDigest

/**
 * A [Session] as its item in the games table. The token itself is never stored: the item's id is `session#` plus the
 * token's SHA-256, so a copy of the table can't be replayed as live sessions, and [toSession] takes the token back
 * from the caller who presented it.
 */
internal class SessionItem(val attributes: Map<String, AttributeValue>) {

	constructor(session: Session) : this(
		mapOf(
			ID to AttributeValue.fromS(sessionItemId(session.token)),
			PLAYER_NAME to AttributeValue.fromS(session.playerName),
			CORPORATION_NAME to AttributeValue.fromS(session.corporationName),
			EMAIL to AttributeValue.fromS(session.email)
		)
	)

	fun toSession(token: String): Session = Session(
		token = token,
		playerName = attribute(PLAYER_NAME),
		corporationName = attribute(CORPORATION_NAME),
		email = attribute(EMAIL)
	)

	private fun attribute(name: String): String =
		attributes[name]?.s() ?: throw IllegalStateException("Stored session item has no '$name' attribute")

	companion object {
		const val ID = "id"
		private const val PLAYER_NAME = "playerName"
		private const val CORPORATION_NAME = "corporationName"
		private const val EMAIL = "email"
	}
}

/** Game ids are UUIDs and never contain `#`, so a session's id can't collide with a game's. */
internal fun sessionItemId(token: String): String {
	val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
	return "session#" + digest.joinToString("") { "%02x".format(it) }
}
