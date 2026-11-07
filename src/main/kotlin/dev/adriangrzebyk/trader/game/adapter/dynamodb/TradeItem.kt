package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Trade
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Trade] as the attribute map nested in its resolution (engineering standards §12). */
internal class TradeItem(val attributes: Map<String, AttributeValue>) {

	constructor(trade: Trade) : this(
		mapOf(
			OFFER to AttributeValue.fromM(OfferItem(trade.offer).attributes),
			UNITS to number(trade.units)
		)
	)

	fun toTrade(): Trade = Trade(
		offer = OfferItem(attributes.attribute(OFFER).m()).toOffer(),
		units = attributes.attribute(UNITS).n().toInt()
	)

	private companion object {
		const val OFFER = "offer"
		const val UNITS = "units"
	}
}
