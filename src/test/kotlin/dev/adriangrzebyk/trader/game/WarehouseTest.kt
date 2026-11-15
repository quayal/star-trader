package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WarehouseTest {

	@Test
	fun shouldHoldTheCargoSpacesOfAShipForEachBlock() {
		assertThat(Warehouse(StarSystem.TAU_CETI, 1).cargoSpaces()).isEqualTo(Ship.CARGO_SPACES)
		assertThat(Warehouse(StarSystem.TAU_CETI, 3).cargoSpaces()).isEqualTo(30)
	}
}
