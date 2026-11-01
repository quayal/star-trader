package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Turn] as the attribute map nested in the game item (engineering standards §12). */
internal class TurnItem(val attributes: Map<String, AttributeValue>) {

	constructor(turn: Turn) : this(
		mapOf(
			NUMBER to number(turn.number),
			PHASE to AttributeValue.fromS(turn.phase.name)
		)
	)

	fun toTurn(): Turn = Turn(
		number = attributes.attribute(NUMBER).n().toInt(),
		phase = TurnPhase.valueOf(attributes.attribute(PHASE).s())
	)

	private companion object {
		const val NUMBER = "number"
		const val PHASE = "phase"
	}
}
