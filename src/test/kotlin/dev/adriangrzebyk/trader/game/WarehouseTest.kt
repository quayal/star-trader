package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WarehouseTest {

	@Test
	fun shouldHoldTheCargoSpacesOfAShipForEachBlock() {
		assertThat(Warehouse(StarSystem.TAU_CETI, 1, emptyMap()).cargoSpaces()).isEqualTo(Ship.CARGO_SPACES)
		assertThat(Warehouse(StarSystem.TAU_CETI, 3, emptyMap()).cargoSpaces()).isEqualTo(30)
	}

	@Test
	fun shouldCountOneSpacePerUnitOfPolyAndIsoOnly() {
		// R42, the same as a ship
		val warehouse = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 4, Goods.ISO to 3, Goods.CHIPS to 40))

		assertThat(warehouse.cargoSpacesUsed()).isEqualTo(7)
		assertThat(warehouse.freeSpacesFor(Goods.POLY)).isEqualTo(3)
		assertThat(warehouse.freeSpacesFor(Goods.CHIPS)).isNull()
	}

	@Test
	fun shouldStorePolyAndIsoUpToItsFreeSpaceAndLoseTheRest() {
		val warehouse = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 8))

		assertThat(warehouse.load(Goods.ISO, 5).holding(Goods.ISO)).isEqualTo(2)
		assertThat(warehouse.load(Goods.POLY, 5).holding(Goods.POLY)).isEqualTo(10)
	}

	@Test
	fun shouldStoreAnyAmountOfChipsAndFood() {
		val full = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 10))

		assertThat(full.load(Goods.FOOD, 60).holding(Goods.FOOD)).isEqualTo(60)
	}
}
