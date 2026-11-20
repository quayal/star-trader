package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.news.ActiveTipOff
import dev.adriangrzebyk.trader.game.news.News
import dev.adriangrzebyk.trader.game.news.TipOff
import dev.adriangrzebyk.trader.game.news.TurnConditions
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A game's [News] as the attribute map nested in its item (engineering standards §12): tip-offs by their table ids. */
internal class NewsItem(val attributes: Map<String, AttributeValue>) {

	constructor(news: News) : this(
		mapOf(
			POOL to strings(news.pool.map { it.name }),
			RETIRED to strings(news.retired.map { it.name }),
			NEXT_SLOT to number(news.nextSlot),
			IN_PLAY to AttributeValue.fromL(
				news.inPlay.map {
					AttributeValue.fromM(
						mapOf(
							SLOT to number(it.slot),
							TIP_OFF to AttributeValue.fromS(it.tipOff.name),
							DRAWN_IN to number(it.drawnIn),
							LEARNED_BY to strings(it.learnedBy)
						) + listOfNotNull(it.takenBy?.let { email -> TAKEN_BY to AttributeValue.fromS(email) })
					)
				}
			),
			CONDITIONS_TURN to number(news.conditions.turn),
			JUMP_MODIFIER to number(news.conditions.jumpModifier),
			PORTS_CLOSED to AttributeValue.fromBool(news.conditions.portsClosed),
			SECURITY_CHANGE to number(news.conditions.securityChange),
			POLICE_CHANGE to number(news.conditions.policeChange)
		) + listOfNotNull(
			news.conditions.tripledWeaponsAt?.let { TRIPLED_WEAPONS_AT to AttributeValue.fromS(it.name) },
			news.noShipsThroughTurn?.let { NO_SHIPS_THROUGH to number(it) }
		)
	)

	fun toNews(): News = News(
		pool = names(POOL).map { TipOff.valueOf(it) },
		inPlay = attributes.attribute(IN_PLAY).l().map { item ->
			val m = item.m()
			ActiveTipOff(
				m.attribute(SLOT).n().toInt(),
				TipOff.valueOf(m.attribute(TIP_OFF).s()),
				m.attribute(DRAWN_IN).n().toInt(),
				m.attribute(LEARNED_BY).l().map { it.s() },
				m[TAKEN_BY]?.s()
			)
		},
		retired = names(RETIRED).map { TipOff.valueOf(it) },
		nextSlot = attributes.attribute(NEXT_SLOT).n().toInt(),
		conditions = TurnConditions(
			attributes.attribute(CONDITIONS_TURN).n().toInt(),
			attributes.attribute(JUMP_MODIFIER).n().toInt(),
			attributes.attribute(PORTS_CLOSED).bool(),
			attributes.attribute(SECURITY_CHANGE).n().toInt(),
			attributes.attribute(POLICE_CHANGE).n().toInt(),
			attributes[TRIPLED_WEAPONS_AT]?.s()?.let { StarSystem.valueOf(it) }
		),
		noShipsThroughTurn = attributes[NO_SHIPS_THROUGH]?.n()?.toInt()
	)

	private fun names(name: String): List<String> = attributes.attribute(name).l().map { it.s() }

	private companion object {
		const val POOL = "pool"
		const val RETIRED = "retired"
		const val NEXT_SLOT = "nextSlot"
		const val IN_PLAY = "inPlay"
		const val SLOT = "slot"
		const val TIP_OFF = "tipOff"
		const val DRAWN_IN = "drawnIn"
		const val LEARNED_BY = "learnedBy"
		const val TAKEN_BY = "takenBy"
		const val CONDITIONS_TURN = "conditionsTurn"
		const val JUMP_MODIFIER = "jumpModifier"
		const val PORTS_CLOSED = "portsClosed"
		const val SECURITY_CHANGE = "securityChange"
		const val POLICE_CHANGE = "policeChange"
		const val TRIPLED_WEAPONS_AT = "tripledWeaponsAt"
		const val NO_SHIPS_THROUGH = "noShipsThroughTurn"
	}
}
