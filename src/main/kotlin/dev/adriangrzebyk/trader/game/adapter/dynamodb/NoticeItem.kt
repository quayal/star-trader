package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Notice
import dev.adriangrzebyk.trader.game.NoticeKind
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Notice] as the attribute map nested in its log entry (engineering standards §12); `round` and `interception` only when set. */
internal class NoticeItem(val attributes: Map<String, AttributeValue>) {

	constructor(notice: Notice) : this(
		mapOf(
			KIND to AttributeValue.fromS(notice.kind.name),
			SYSTEM to AttributeValue.fromS(notice.system.name),
			PLAYERS to strings(notice.players)
		) + listOfNotNull(
			notice.round?.let { ROUND to number(it) },
			notice.interception?.let { INTERCEPTION to AttributeValue.fromM(InterceptionRollItem(it).attributes) }
		)
	)

	fun toNotice(): Notice = Notice(
		kind = NoticeKind.valueOf(attributes.attribute(KIND).s()),
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		players = attributes.attribute(PLAYERS).l().map { it.s() },
		round = attributes[ROUND]?.n()?.toInt(),
		interception = attributes[INTERCEPTION]?.m()?.let { InterceptionRollItem(it).toInterceptionRoll() }
	)

	private companion object {
		const val KIND = "kind"
		const val SYSTEM = "system"
		const val PLAYERS = "players"
		const val ROUND = "round"
		const val INTERCEPTION = "interception"
	}
}
