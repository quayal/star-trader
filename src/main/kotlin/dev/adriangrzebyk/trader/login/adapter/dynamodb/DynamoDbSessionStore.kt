package dev.adriangrzebyk.trader.login.adapter.dynamodb

import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * Keeps each session as one item in the games table, so a player stays signed in across a server restart.
 * Reads are consistent, so a session works the moment the code exchange returns its token.
 */
class DynamoDbSessionStore(private val client: DynamoDbClient, private val tableName: String) : SessionStore {

	override fun store(session: Session) {
		client.putItem { it.tableName(tableName).item(SessionItem(session).attributes) }
	}

	override fun find(token: String): Session? = client.getItem {
		it.tableName(tableName)
			.key(mapOf(SessionItem.ID to AttributeValue.fromS(sessionItemId(token))))
			.consistentRead(true)
	}.takeIf { it.hasItem() }?.let { SessionItem(it.item()).toSession(token) }
}
