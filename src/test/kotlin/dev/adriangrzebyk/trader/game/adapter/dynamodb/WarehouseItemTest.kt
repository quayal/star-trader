package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Warehouse
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Warehouse extends this round trip in the same PR (engineering standards §12).
class WarehouseItemTest {

	private val warehouse = Warehouse(system = StarSystem.TAU_CETI, blocks = 3, cargo = mapOf(Goods.POLY to 12, Goods.CHIPS to 40))

	@Test
	fun shouldReadBackEveryFieldOfAWarehouse() {
		assertThat(WarehouseItem(WarehouseItem(warehouse).attributes).toWarehouse()).isEqualTo(warehouse)
	}

	@Test
	fun shouldStoreAWarehouseUnderTheseAttributeNames() {
		assertThat(WarehouseItem(warehouse).attributes).isEqualTo(
			mapOf(
				"system" to AttributeValue.fromS("TAU_CETI"),
				"blocks" to AttributeValue.fromN("3"),
				"cargo" to AttributeValue.fromM(mapOf("POLY" to AttributeValue.fromN("12"), "CHIPS" to AttributeValue.fromN("40")))
			)
		)
	}

	@Test
	fun shouldReadAWarehouseSavedBeforeItHeldCargoAsEmpty() {
		// E06-S06: items from E06-S02 have no cargo (BACKWARD_COMPATIBILITY.md §7)
		val older = WarehouseItem(warehouse).attributes - "cargo"

		assertThat(WarehouseItem(older).toWarehouse()).isEqualTo(warehouse.copy(cargo = emptyMap()))
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredWarehouseIsIncomplete() {
		assertThatThrownBy { WarehouseItem(WarehouseItem(warehouse).attributes - "blocks").toWarehouse() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("blocks")
	}
}
