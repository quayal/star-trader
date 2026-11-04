package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.InitiativeBid
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** An [InitiativeBid] as the attribute map nested in its turn's item (engineering standards §12). */
internal class InitiativeBidItem(val attributes: Map<String, AttributeValue>) {

	constructor(bid: InitiativeBid) : this(
		mapOf(
			PLAYER_EMAIL to AttributeValue.fromS(bid.playerEmail),
			AMOUNT to number(bid.amount)
		)
	)

	fun toBid(): InitiativeBid = InitiativeBid(
		playerEmail = attributes.attribute(PLAYER_EMAIL).s(),
		amount = attributes.attribute(AMOUNT).n().toInt()
	)

	private companion object {
		const val PLAYER_EMAIL = "playerEmail"
		const val AMOUNT = "amount"
	}
}
