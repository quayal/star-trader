package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Warehouse
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Warehouse] as the attribute map nested in its player's item (engineering standards §12). */
internal class WarehouseItem(val attributes: Map<String, AttributeValue>) {

	constructor(warehouse: Warehouse) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(warehouse.system.name),
			BLOCKS to number(warehouse.blocks)
		)
	)

	fun toWarehouse(): Warehouse = Warehouse(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		blocks = attributes.attribute(BLOCKS).n().toInt()
	)

	private companion object {
		const val SYSTEM = "system"
		const val BLOCKS = "blocks"
	}
}
