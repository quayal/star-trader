package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.LogEntry
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * A [LogEntry] as one element of the game item's `log` list (engineering standards §12): a `{ turn, text }` map.
 * Items written before E03-S06 hold plain strings with no turn; those read as turn 1, the only turn most of them
 * reached (BACKWARD_COMPATIBILITY.md §7).
 */
internal class LogEntryItem(val attribute: AttributeValue) {

	constructor(entry: LogEntry) : this(
		AttributeValue.fromM(
			mapOf(
				TURN to number(entry.turn),
				TEXT to AttributeValue.fromS(entry.text)
			)
		)
	)

	fun toLogEntry(): LogEntry = attribute.s()?.let { LogEntry(turn = OLDER_ITEM_TURN, text = it) } ?: LogEntry(
		turn = attribute.m().attribute(TURN).n().toInt(),
		text = attribute.m().attribute(TEXT).s()
	)

	private companion object {
		const val TURN = "turn"
		const val TEXT = "text"
		const val OLDER_ITEM_TURN = 1
	}
}
