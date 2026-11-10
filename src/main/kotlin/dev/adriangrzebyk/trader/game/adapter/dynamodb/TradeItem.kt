package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Trade
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Trade] as the attribute map nested in its resolution (engineering standards §12). */
internal class TradeItem(val attributes: Map<String, AttributeValue>) {

	constructor(trade: Trade) : this(
		mapOf(
			OFFER to AttributeValue.fromM(OfferItem(trade.offer).attributes),
			MARKET_MAX to number(trade.marketMax),
			UNITS to number(trade.units)
		) + listOfNotNull(trade.positionRoll?.let { POSITION_ROLL to AttributeValue.fromM(PositionRollItem(it).attributes) })
	)

	fun toTrade(): Trade {
		val units = attributes.attribute(UNITS).n().toInt()
		return Trade(
			offer = OfferItem(attributes.attribute(OFFER).m()).toOffer(),
			// Absent on trades saved before E05-S04, when every offer traded the most the market allowed.
			marketMax = attributes[MARKET_MAX]?.n()?.toInt() ?: units,
			units = units,
			// Absent when the trade made no position roll, and on every trade saved before E05-S06.
			positionRoll = attributes[POSITION_ROLL]?.m()?.let { PositionRollItem(it).toPositionRoll() }
		)
	}

	private companion object {
		const val OFFER = "offer"
		const val MARKET_MAX = "marketMax"
		const val UNITS = "units"
		const val POSITION_ROLL = "positionRoll"
	}
}
