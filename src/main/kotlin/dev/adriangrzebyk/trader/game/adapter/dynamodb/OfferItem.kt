package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** An [Offer] as the attribute map nested in its turn's item (engineering standards §12). */
internal class OfferItem(val attributes: Map<String, AttributeValue>) {

	constructor(offer: Offer) : this(
		mapOf(
			PLAYER_EMAIL to AttributeValue.fromS(offer.playerEmail),
			SYSTEM to AttributeValue.fromS(offer.system.name),
			GOODS to AttributeValue.fromS(offer.goods.name),
			TYPE to AttributeValue.fromS(offer.type.name),
			PRICE to number(offer.price)
		)
	)

	fun toOffer(): Offer = Offer(
		playerEmail = attributes.attribute(PLAYER_EMAIL).s(),
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		goods = Goods.valueOf(attributes.attribute(GOODS).s()),
		type = OfferType.valueOf(attributes.attribute(TYPE).s()),
		price = attributes.attribute(PRICE).n().toInt()
	)

	private companion object {
		const val PLAYER_EMAIL = "playerEmail"
		const val SYSTEM = "system"
		const val GOODS = "goods"
		const val TYPE = "type"
		const val PRICE = "price"
	}
}
