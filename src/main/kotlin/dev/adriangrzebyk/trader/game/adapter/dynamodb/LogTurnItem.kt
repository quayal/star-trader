package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.LogEntry
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * One turn of a game's log as its own DynamoDB item, beside the game item (BACKWARD_COMPATIBILITY.md §7): the log grows
 * with every good resolved, and inside the game item a long game would pass DynamoDB's 400 KB limit for one item. Its
 * [ID] is [logTurnItemId]; [entries] are that turn's, in the order they happened, each a [LogEntryItem].
 */
internal class LogTurnItem(val attributes: Map<String, AttributeValue>) {

	constructor(gameId: String, turn: Int, entries: List<LogEntry>) : this(
		mapOf(
			ID to AttributeValue.fromS(logTurnItemId(gameId, turn)),
			TURN to number(turn),
			ENTRIES to AttributeValue.fromL(entries.map { LogEntryItem(it).attribute })
		)
	)

	val turn: Int
		get() = attributes.attribute(TURN).n().toInt()

	fun entries(): List<LogEntry> = attributes.attribute(ENTRIES).l().map { LogEntryItem(it).toLogEntry() }

	companion object {
		const val ID = GameItem.ID
		private const val TURN = "turn"
		private const val ENTRIES = "entries"
	}
}

/** The id of the item holding turn [turn] of game [gameId]'s log. A game id has no `#`, so it's never taken for a game. */
internal fun logTurnItemId(gameId: String, turn: Int): String = "log#$gameId#$turn"
