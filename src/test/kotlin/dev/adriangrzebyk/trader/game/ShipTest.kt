package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

	@Test
	fun shouldArriveInSpaceAtTheDestinationWhenJumping() {
		val ship = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)

		assertThat(ship.jumpTo(StarSystem.MU_HERCULIS)).isEqualTo(Ship.emptyAt("Wayfarer", StarSystem.MU_HERCULIS, ShipLocation.SPACE))
	}

	@Test
	fun shouldRefuseAJumpToTheSystemItIsAlreadyAt() {
		// S02-4
		val ship = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)

		assertThatThrownBy { ship.jumpTo(StarSystem.TAU_CETI) }
			.isInstanceOf(InvalidJumpException::class.java)
			.hasMessage("Wayfarer is already at Tau Ceti. Pick another destination.")
	}

	@Test
	fun shouldStayInItsSystemWhenLanding() {
		val ship = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.SPACE)

		assertThat(ship.landAt(ShipLocation.PLANET)).isEqualTo(Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PLANET))
	}

	@Test
	fun shouldDescribeItsLandingForTheLog() {
		val ship = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)

		assertThat(ship.landingLog()).isEqualTo("Wayfarer lands at the port (Tau Ceti)")
	}

	@Test
	fun shouldLoadPolyAndIsoUpToItsFreeSpaceAndLoseTheRest() {
		// R42, R16: 4 of 10 spaces used, so 6 of 8 ISO fit.
		val ship = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.POLY to 4, Goods.CHIPS to 12))

		assertThat(ship.unitsThatFit(Goods.ISO, 8)).isEqualTo(6)
		assertThat(ship.load(Goods.ISO, 8).cargo).isEqualTo(mapOf(Goods.POLY to 4, Goods.CHIPS to 12, Goods.ISO to 6))
	}

	@Test
	fun shouldLoadAnyAmountOfChipsAndFood() {
		val full = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.POLY to 10))

		assertThat(full.unitsThatFit(Goods.FOOD, 30)).isEqualTo(30)
		assertThat(full.load(Goods.FOOD, 30).holding(Goods.FOOD)).isEqualTo(30)
		assertThat(full.load(Goods.ISO, 3).holding(Goods.ISO)).isZero()
	}

	@Test
	fun shouldUnloadWhatItSoldAndDropAGoodItNoLongerCarries() {
		val ship = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.ISO to 5, Goods.CHIPS to 2))

		assertThat(ship.unload(Goods.ISO, 2).cargo).isEqualTo(mapOf(Goods.ISO to 3, Goods.CHIPS to 2))
		assertThat(ship.unload(Goods.ISO, 5).cargo).isEqualTo(mapOf(Goods.CHIPS to 2))
	}

	@Test
	fun shouldRefuseToUnloadMoreThanItHolds() {
		// R10
		val ship = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.ISO to 2))

		assertThatThrownBy { ship.unload(Goods.ISO, 3) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Wayfarer has only 2 ISO.")
	}

	@Test
	fun shouldFitNothingInAnOverfullShip() {
		val overfull = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.POLY to 12))

		assertThat(overfull.unitsThatFit(Goods.ISO, 3)).isZero()
		assertThat(overfull.load(Goods.ISO, 3).holding(Goods.ISO)).isZero()
	}
}
