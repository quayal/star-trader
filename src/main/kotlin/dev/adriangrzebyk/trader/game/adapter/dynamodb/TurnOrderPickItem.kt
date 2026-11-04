package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.TurnOrderPick
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [TurnOrderPick] as the attribute map nested in its turn's item (engineering standards §12). */
internal class TurnOrderPickItem(val attributes: Map<String, AttributeValue>) {

	constructor(pick: TurnOrderPick) : this(
		mapOf(
			PLAYER_EMAIL to AttributeValue.fromS(pick.playerEmail),
			PLACE to number(pick.place)
		)
	)

	fun toPick(): TurnOrderPick = TurnOrderPick(
		playerEmail = attributes.attribute(PLAYER_EMAIL).s(),
		place = attributes.attribute(PLACE).n().toInt()
	)

	private companion object {
		const val PLAYER_EMAIL = "playerEmail"
		const val PLACE = "place"
	}
}
