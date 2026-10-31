package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Turn] as the attribute map nested in the game item (engineering standards §12). */
internal object TurnAttributes {

	private const val NUMBER = "number"
	private const val PHASE = "phase"

	fun toAttributes(turn: Turn): Map<String, AttributeValue> = mapOf(
		NUMBER to number(turn.number),
		PHASE to AttributeValue.fromS(turn.phase.name),
	)

	fun from(attributes: Map<String, AttributeValue>): Turn = Turn(
		number = attributes.attribute(NUMBER).n().toInt(),
		phase = TurnPhase.valueOf(attributes.attribute(PHASE).s()),
	)
}
