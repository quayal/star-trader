package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Warehouse
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Warehouse extends this round trip in the same PR (engineering standards §12).
class WarehouseItemTest {

	private val warehouse = Warehouse(system = StarSystem.TAU_CETI, blocks = 3)

	@Test
	fun shouldReadBackEveryFieldOfAWarehouse() {
		assertThat(WarehouseItem(WarehouseItem(warehouse).attributes).toWarehouse()).isEqualTo(warehouse)
	}

	@Test
	fun shouldStoreAWarehouseUnderTheseAttributeNames() {
		assertThat(WarehouseItem(warehouse).attributes).isEqualTo(
			mapOf(
				"system" to AttributeValue.fromS("TAU_CETI"),
				"blocks" to AttributeValue.fromN("3")
			)
		)
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredWarehouseIsIncomplete() {
		assertThatThrownBy { WarehouseItem(WarehouseItem(warehouse).attributes - "blocks").toWarehouse() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("blocks")
	}
}
