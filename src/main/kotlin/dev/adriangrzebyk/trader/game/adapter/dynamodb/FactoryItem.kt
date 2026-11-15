package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Factory] as the attribute map nested in its player's item (engineering standards §12). */
internal class FactoryItem(val attributes: Map<String, AttributeValue>) {

	constructor(factory: Factory) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(factory.system.name),
			GOODS to AttributeValue.fromS(factory.goods.name),
			SIZE to number(factory.size)
		)
	)

	fun toFactory(): Factory = Factory(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		goods = Goods.valueOf(attributes.attribute(GOODS).s()),
		size = attributes.attribute(SIZE).n().toInt()
	)

	private companion object {
		const val SYSTEM = "system"
		const val GOODS = "goods"
		const val SIZE = "size"
	}
}
