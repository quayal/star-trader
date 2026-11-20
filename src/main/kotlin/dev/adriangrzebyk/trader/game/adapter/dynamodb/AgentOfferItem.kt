package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentOffer
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** An [AgentOffer] as the attribute map nested in its turn's item (engineering standards §12). */
internal class AgentOfferItem(val attributes: Map<String, AttributeValue>) {

	constructor(offer: AgentOffer) : this(
		mapOf(
			ID to number(offer.id),
			SELLER to AttributeValue.fromS(offer.sellerEmail),
			BUYER to AttributeValue.fromS(offer.buyerEmail),
			AGENT to AttributeValue.fromS(offer.agent.name),
			PRICE to number(offer.price)
		)
	)

	fun toAgentOffer(): AgentOffer = AgentOffer(
		id = attributes.attribute(ID).n().toInt(),
		sellerEmail = attributes.attribute(SELLER).s(),
		buyerEmail = attributes.attribute(BUYER).s(),
		agent = Agent.valueOf(attributes.attribute(AGENT).s()),
		price = attributes.attribute(PRICE).n().toInt()
	)

	private companion object {
		const val ID = "id"
		const val SELLER = "seller"
		const val BUYER = "buyer"
		const val AGENT = "agent"
		const val PRICE = "price"
	}
}
