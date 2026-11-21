package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// E12: pirates empty a warehouse, and the log says how many units it lost, never which goods (as R101 does for sabotage).
class PlayerLootTest {

	private val player = Player.newcomer("grace@example.com", "Grace", "Vega Lines").copy(
		warehouses = listOf(
			Warehouse(MU_HERCULIS, 1, mapOf(Goods.CHIPS to 4, Goods.ISO to 2)),
			Warehouse(TAU_CETI, 1, mapOf(Goods.FOOD to 3))
		)
	)

	@Test
	fun shouldEmptyTheWarehouseAndSayHowManyUnitsItLost() {
		val looted = player.lootWarehouse(MU_HERCULIS)

		assertThat(looted.player.warehouseAt(MU_HERCULIS)!!.unitsHeld()).isZero()
		assertThat(looted.player.warehouseAt(TAU_CETI)!!.holding(Goods.FOOD)).isEqualTo(3)
		assertThat(looted.lines).containsExactly("Pirates at Mu Herculis: Vega Lines's warehouse lost 6 u")
	}

	@Test
	fun shouldLogNothingForAnEmptyWarehouseOrNone() {
		val empty = player.copy(warehouses = listOf(Warehouse(MU_HERCULIS, 1, emptyMap())))

		assertThat(empty.lootWarehouse(MU_HERCULIS).lines).isEmpty()
		assertThat(Player.newcomer("ada@example.com", "Ada", "Orion Freight").lootWarehouse(MU_HERCULIS).lines).isEmpty()
	}
}
