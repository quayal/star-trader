package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [MarketPosition] as the attribute map nested in its player's item (engineering standards §12). */
internal class MarketPositionItem(val attributes: Map<String, AttributeValue>) {

	constructor(position: MarketPosition) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(position.system.name),
			GOODS to AttributeValue.fromS(position.goods.name),
			TIER to AttributeValue.fromS(position.tier.name)
		)
	)

	fun toMarketPosition(): MarketPosition = MarketPosition(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		goods = Goods.valueOf(attributes.attribute(GOODS).s()),
		tier = MarketTier.valueOf(attributes.attribute(TIER).s())
	)

	private companion object {
		const val SYSTEM = "system"
		const val GOODS = "goods"
		const val TIER = "tier"
	}
}
