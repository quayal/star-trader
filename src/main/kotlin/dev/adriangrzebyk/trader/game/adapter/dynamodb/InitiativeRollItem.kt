package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.InitiativeRoll
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** An [InitiativeRoll] as the attribute map nested in its turn's item (engineering standards §12). */
internal class InitiativeRollItem(val attributes: Map<String, AttributeValue>) {

	constructor(roll: InitiativeRoll) : this(
		mapOf(
			PLAYER_EMAIL to AttributeValue.fromS(roll.playerEmail),
			BID to number(roll.bid),
			ROLLS to AttributeValue.fromL(roll.rolls.map { number(it) })
		)
	)

	fun toInitiativeRoll(): InitiativeRoll = InitiativeRoll(
		playerEmail = attributes.attribute(PLAYER_EMAIL).s(),
		bid = attributes.attribute(BID).n().toInt(),
		rolls = attributes.attribute(ROLLS).l().map { it.n().toInt() }
	)

	private companion object {
		const val PLAYER_EMAIL = "playerEmail"
		const val BID = "bid"
		const val ROLLS = "rolls"
	}
}
