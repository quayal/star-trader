package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.Warehouse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Warehouse extends this round trip in the same PR (engineering standards §12).
class WarehouseItemTest {

	private val warehouse = Warehouse(
		system = StarSystem.TAU_CETI,
		blocks = 3,
		cargo = mapOf(Goods.POLY to 7, Goods.FOOD to 12),
		modules = listOf(Module.CARGO, Module.PASSENGER)
	)

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
				"cargo" to AttributeValue.fromM(mapOf("POLY" to AttributeValue.fromN("7"), "FOOD" to AttributeValue.fromN("12"))),
				"modules" to AttributeValue.fromL(listOf(AttributeValue.fromS("CARGO"), AttributeValue.fromS("PASSENGER")))
			)
		)
	}

	@Test
	fun shouldReadAWarehouseStoredBeforeItCouldHoldGoodsAsEmpty() {
		// Items saved before E06-S03 have no cargo (BACKWARD_COMPATIBILITY.md §7).
		val older = WarehouseItem(warehouse).attributes - "cargo"

		assertThat(WarehouseItem(older).toWarehouse()).isEqualTo(warehouse.copy(cargo = emptyMap()))
	}

	@Test
	fun shouldReadAWarehouseStoredBeforeItCouldHoldModulesAsHoldingNone() {
		// Items saved before 2026-11-23 have no modules (R122, BACKWARD_COMPATIBILITY.md §7).
		val older = WarehouseItem(warehouse).attributes - "modules"

		assertThat(WarehouseItem(older).toWarehouse()).isEqualTo(warehouse.copy(modules = emptyList()))
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredWarehouseIsIncomplete() {
		assertThatThrownBy { WarehouseItem(WarehouseItem(warehouse).attributes - "blocks").toWarehouse() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("blocks")
	}
}
