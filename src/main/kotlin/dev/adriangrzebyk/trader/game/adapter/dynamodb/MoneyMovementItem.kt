package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.ledger.MoneyMovement
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [MoneyMovement] as the attribute map nested in its player's ledger (engineering standards §12). */
internal class MoneyMovementItem(val attributes: Map<String, AttributeValue>) {

	constructor(movement: MoneyMovement) : this(
		mapOf(
			TURN to number(movement.turn),
			REASON to AttributeValue.fromS(movement.reason),
			AMOUNT to number(movement.amount)
		)
	)

	fun toMoneyMovement(): MoneyMovement = MoneyMovement(
		turn = attributes.attribute(TURN).n().toInt(),
		reason = attributes.attribute(REASON).s(),
		amount = attributes.attribute(AMOUNT).n().toInt()
	)

	private companion object {
		const val TURN = "turn"
		const val REASON = "reason"
		const val AMOUNT = "amount"
	}
}
