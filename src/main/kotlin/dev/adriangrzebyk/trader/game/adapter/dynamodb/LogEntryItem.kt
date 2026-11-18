package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.LogEntry
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * A [LogEntry] as one element of the game item's `log` list (engineering standards §12): a `{ turn, text }` map, with
 * `trade` when the entry tells of a trade and `notice` when it tells of an interception or battle moment.
 * Items written before E03-S06 hold plain strings with no turn; those read as turn 1, the only turn most of them
 * reached (BACKWARD_COMPATIBILITY.md §7).
 */
internal class LogEntryItem(val attribute: AttributeValue) {

	constructor(entry: LogEntry) : this(
		AttributeValue.fromM(
			mapOf(
				TURN to number(entry.turn),
				TEXT to AttributeValue.fromS(entry.text)
			) + listOfNotNull(
				entry.trade?.let { TRADE to AttributeValue.fromM(TradeRecordItem(it).attributes) },
				entry.notice?.let { NOTICE to AttributeValue.fromM(NoticeItem(it).attributes) }
			)
		)
	)

	fun toLogEntry(): LogEntry = attribute.s()?.let { LogEntry(turn = OLDER_ITEM_TURN, text = it, trade = null, notice = null) } ?: LogEntry(
		turn = attribute.m().attribute(TURN).n().toInt(),
		text = attribute.m().attribute(TEXT).s(),
		// Absent on every entry that isn't a trade, and on entries logged before 2026-11-18.
		trade = attribute.m()[TRADE]?.m()?.let { TradeRecordItem(it).toTradeRecord() },
		// Absent on every entry that isn't an interception or battle moment, and on entries logged before 2026-11-18.
		notice = attribute.m()[NOTICE]?.m()?.let { NoticeItem(it).toNotice() }
	)

	private companion object {
		const val TURN = "turn"
		const val TEXT = "text"
		const val TRADE = "trade"
		const val NOTICE = "notice"
		const val OLDER_ITEM_TURN = 1
	}
}
