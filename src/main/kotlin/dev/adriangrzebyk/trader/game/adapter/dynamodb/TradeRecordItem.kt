package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.TradeRecord
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [TradeRecord] as the attribute map nested in its log entry (engineering standards §12). */
internal class TradeRecordItem(val attributes: Map<String, AttributeValue>) {

	constructor(record: TradeRecord) : this(
		mapOf(
			PLAYER_EMAIL to AttributeValue.fromS(record.playerEmail),
			SYSTEM to AttributeValue.fromS(record.system.name),
			GOODS to AttributeValue.fromS(record.goods.name),
			TYPE to AttributeValue.fromS(record.type.name),
			UNITS to number(record.units),
			PRICE to number(record.price)
		)
	)

	fun toTradeRecord(): TradeRecord = TradeRecord(
		playerEmail = attributes.attribute(PLAYER_EMAIL).s(),
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		goods = Goods.valueOf(attributes.attribute(GOODS).s()),
		type = OfferType.valueOf(attributes.attribute(TYPE).s()),
		units = attributes.attribute(UNITS).n().toInt(),
		price = attributes.attribute(PRICE).n().toInt()
	)

	private companion object {
		const val PLAYER_EMAIL = "playerEmail"
		const val SYSTEM = "system"
		const val GOODS = "goods"
		const val TYPE = "type"
		const val UNITS = "units"
		const val PRICE = "price"
	}
}
