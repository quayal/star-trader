package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.Warehouse
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Warehouse] as the attribute map nested in its player's item (engineering standards §12). */
internal class WarehouseItem(val attributes: Map<String, AttributeValue>) {

	constructor(warehouse: Warehouse) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(warehouse.system.name),
			BLOCKS to number(warehouse.blocks),
			CARGO to AttributeValue.fromM(warehouse.cargo.entries.associate { (goods, units) -> goods.name to number(units) })
		)
	)

	fun toWarehouse(): Warehouse = Warehouse(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		blocks = attributes.attribute(BLOCKS).n().toInt(),
		// Items saved before E06-S03 have no cargo; nothing could be stored in a warehouse then (BACKWARD_COMPATIBILITY.md §7).
		cargo = attributes[CARGO]?.m()?.entries?.associate { (goods, units) -> Goods.valueOf(goods) to units.n().toInt() } ?: emptyMap()
	)

	private companion object {
		const val SYSTEM = "system"
		const val BLOCKS = "blocks"
		const val CARGO = "cargo"
	}
}
