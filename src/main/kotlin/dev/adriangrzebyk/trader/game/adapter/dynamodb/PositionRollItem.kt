package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.game.PositionRoll
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [PositionRoll] as the attribute map nested in its trade (engineering standards §12). */
internal class PositionRollItem(val attributes: Map<String, AttributeValue>) {

	constructor(positionRoll: PositionRoll) : this(
		mapOf(
			ROLL to number(positionRoll.roll),
			DEMOTED to AttributeValue.fromL(positionRoll.demoted.map { AttributeValue.fromS(it) })
		) + listOfNotNull(positionRoll.promotedTo?.let { PROMOTED_TO to AttributeValue.fromS(it.name) })
	)

	fun toPositionRoll(): PositionRoll = PositionRoll(
		roll = attributes.attribute(ROLL).n().toInt(),
		// Absent when the roll changed nothing.
		promotedTo = attributes[PROMOTED_TO]?.s()?.let { MarketTier.valueOf(it) },
		demoted = attributes.attribute(DEMOTED).l().map { it.s() }
	)

	private companion object {
		const val ROLL = "roll"
		const val PROMOTED_TO = "promotedTo"
		const val DEMOTED = "demoted"
	}
}
