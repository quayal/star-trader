package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShipTest {

	@Test
	fun shouldUseNoCargoSpaceWhenEmpty() {
		assertThat(Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT).cargoSpacesUsed()).isZero()
	}

	@Test
	fun shouldCountOneSpacePerUnitOfPolyAndIsoOnly() {
		// R42: "Wayfarer — Tau Ceti · Port — POLY 4, ISO 3, CHIPS 12 · cargo 7 / 10 used"
		val ship = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.POLY to 4, Goods.ISO to 3, Goods.CHIPS to 12))

		assertThat(ship.cargoSpacesUsed()).isEqualTo(7)
	}

	@Test
	fun shouldFitAnyAmountOfChipsAndFoodInNoSpace() {
		val ship = Ship("Lantern", StarSystem.BETA_HYDRI, ShipLocation.SPACE, mapOf(Goods.CHIPS to 40, Goods.FOOD to 25))

		assertThat(ship.cargoSpacesUsed()).isZero()
	}
}
