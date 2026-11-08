package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Wholesale
import dev.adriangrzebyk.trader.trading.OfferType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Wholesale] as the attribute map nested in its resolution (engineering standards §12). */
internal class WholesaleItem(val attributes: Map<String, AttributeValue>) {

	constructor(wholesale: Wholesale) : this(
		mapOf(
			PLAYER_EMAIL to AttributeValue.fromS(wholesale.playerEmail),
			UNITS to number(wholesale.units),
			PRICE to number(wholesale.price)
		) + listOfNotNull(wholesale.type?.let { TYPE to AttributeValue.fromS(it.name) })
	)

	fun toWholesale(): Wholesale = Wholesale(
		playerEmail = attributes.attribute(PLAYER_EMAIL).s(),
		// Absent for a pass.
		type = attributes[TYPE]?.s()?.let { OfferType.valueOf(it) },
		units = attributes.attribute(UNITS).n().toInt(),
		price = attributes.attribute(PRICE).n().toInt()
	)

	private companion object {
		const val PLAYER_EMAIL = "playerEmail"
		const val TYPE = "type"
		const val UNITS = "units"
		const val PRICE = "price"
	}
}
