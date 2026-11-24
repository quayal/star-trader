package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Notepad
import dev.adriangrzebyk.trader.game.NotepadStore
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** [NotepadStore] on the games table: a notepad is one item, replaced whole on every save (last write wins). */
class DynamoDbNotepadStore(private val client: DynamoDbClient, private val tableName: String) : NotepadStore {

	override fun find(gameId: String, email: String): Notepad? {
		val item = client.getItem {
			it.tableName(tableName)
				.key(mapOf(NotepadItem.ID to AttributeValue.fromS(notepadItemId(gameId, email))))
				.consistentRead(true)
		}.item()
		return if (item.isEmpty()) null else NotepadItem(item).toNotepad()
	}

	override fun save(gameId: String, email: String, notepad: Notepad) {
		client.putItem { it.tableName(tableName).item(NotepadItem(gameId, email, notepad).attributes) }
	}
}
