package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Notepad
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * One player's notepad as its own DynamoDB item, beside the game item (R135): it's rewritten as the player types, and the
 * game item is rewritten whole on every action. Its [ID] is [notepadItemId].
 */
internal class NotepadItem(val attributes: Map<String, AttributeValue>) {

	constructor(gameId: String, email: String, notepad: Notepad) : this(
		mapOf(ID to AttributeValue.fromS(notepadItemId(gameId, email)), TEXT to AttributeValue.fromS(notepad.text))
	)

	fun toNotepad(): Notepad = Notepad(attributes.attribute(TEXT).s())

	companion object {
		const val ID = GameItem.ID
		private const val TEXT = "text"
	}
}

/** The id of the item holding [email]'s notepad in game [gameId]. A game id has no `#`, so it's never taken for a game. */
internal fun notepadItemId(gameId: String, email: String): String = "notepad#$gameId#$email"
