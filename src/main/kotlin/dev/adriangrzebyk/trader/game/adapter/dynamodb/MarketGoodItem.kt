package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [MarketGood] as the attribute map nested in its turn's item (engineering standards §12). */
internal class MarketGoodItem(val attributes: Map<String, AttributeValue>) {

	constructor(good: MarketGood) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(good.system.name),
			GOODS to AttributeValue.fromS(good.goods.name)
		)
	)

	fun toMarketGood(): MarketGood = MarketGood(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		goods = Goods.valueOf(attributes.attribute(GOODS).s())
	)

	private companion object {
		const val SYSTEM = "system"
		const val GOODS = "goods"
	}
}
