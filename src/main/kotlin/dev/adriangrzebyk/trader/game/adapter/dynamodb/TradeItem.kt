package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Shortfall
import dev.adriangrzebyk.trader.game.Trade
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Trade] as the attribute map nested in its resolution (engineering standards §12). */
internal class TradeItem(val attributes: Map<String, AttributeValue>) {

	constructor(trade: Trade) : this(
		mapOf(
			OFFER to AttributeValue.fromM(OfferItem(trade.offer).attributes),
			UNITS to number(trade.units)
		) + listOfNotNull(trade.shortfall?.let { SHORTFALL to AttributeValue.fromS(it.name) })
	)

	fun toTrade(): Trade = Trade(
		offer = OfferItem(attributes.attribute(OFFER).m()).toOffer(),
		units = attributes.attribute(UNITS).n().toInt(),
		// Absent when units traded, and on every trade saved before E05-S04.
		shortfall = attributes[SHORTFALL]?.s()?.let { Shortfall.valueOf(it) }
	)

	private companion object {
		const val OFFER = "offer"
		const val UNITS = "units"
		const val SHORTFALL = "shortfall"
	}
}
