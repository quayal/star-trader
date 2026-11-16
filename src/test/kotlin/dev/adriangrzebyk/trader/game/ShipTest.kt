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
		val ship =
			Ship(
				"Wayfarer",
				StarSystem.TAU_CETI,
				ShipLocation.PORT,
				mapOf(Goods.POLY to 4, Goods.ISO to 3, Goods.CHIPS to 12),
				Ship.LEGACY_HULL,
				Ship.LEGACY_MODULES
			)

		assertThat(ship.cargoSpacesUsed()).isEqualTo(7)
	}

	@Test
	fun shouldFitAnyAmountOfChipsAndFoodInNoSpace() {
		val ship =
			Ship(
				"Lantern",
				StarSystem.BETA_HYDRI,
				ShipLocation.SPACE,
				mapOf(Goods.CHIPS to 40, Goods.FOOD to 25),
				Ship.LEGACY_HULL,
				Ship.LEGACY_MODULES
			)

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
		val ship =
			Ship(
				"Wayfarer",
				StarSystem.TAU_CETI,
				ShipLocation.PORT,
				mapOf(Goods.POLY to 4, Goods.CHIPS to 12),
				Ship.LEGACY_HULL,
				Ship.LEGACY_MODULES
			)

		assertThat(ship.unitsThatFit(Goods.ISO, 8)).isEqualTo(6)
		assertThat(ship.load(Goods.ISO, 8).cargo).isEqualTo(mapOf(Goods.POLY to 4, Goods.CHIPS to 12, Goods.ISO to 6))
	}

	@Test
	fun shouldLoadAnyAmountOfChipsAndFood() {
		val full = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.POLY to 10), Ship.LEGACY_HULL, Ship.LEGACY_MODULES)

		assertThat(full.unitsThatFit(Goods.FOOD, 30)).isEqualTo(30)
		assertThat(full.load(Goods.FOOD, 30).holding(Goods.FOOD)).isEqualTo(30)
		assertThat(full.load(Goods.ISO, 3).holding(Goods.ISO)).isZero()
	}

	@Test
	fun shouldUnloadWhatItSoldAndDropAGoodItNoLongerCarries() {
		val ship =
			Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.ISO to 5, Goods.CHIPS to 2), Ship.LEGACY_HULL, Ship.LEGACY_MODULES)

		assertThat(ship.unload(Goods.ISO, 2).cargo).isEqualTo(mapOf(Goods.ISO to 3, Goods.CHIPS to 2))
		assertThat(ship.unload(Goods.ISO, 5).cargo).isEqualTo(mapOf(Goods.CHIPS to 2))
	}

	@Test
	fun shouldRefuseToUnloadMoreThanItHolds() {
		// R10
		val ship = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.ISO to 2), Ship.LEGACY_HULL, Ship.LEGACY_MODULES)

		assertThatThrownBy { ship.unload(Goods.ISO, 3) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Wayfarer has only 2 ISO.")
	}

	@Test
	fun shouldFitNothingInAnOverfullShip() {
		val overfull = Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.POLY to 12), Ship.LEGACY_HULL, Ship.LEGACY_MODULES)

		assertThat(overfull.unitsThatFit(Goods.ISO, 3)).isZero()
		assertThat(overfull.load(Goods.ISO, 3).holding(Goods.ISO)).isZero()
	}

	@Test
	fun shouldAddUpTheStatsOfItsHullAndModules() {
		val flute = ship(Hull.FLUTE, Module.ARSENAL, Module.BATTLE_COMM, Module.PASSENGER)

		assertThat(flute.weapons()).isEqualTo(3 + 7 + 6)
		assertThat(flute.rocketWeapons()).isEqualTo(14)
		assertThat(flute.crewNeeded()).isEqualTo(5)
		assertThat(flute.passengers()).isEqualTo(2)
		assertThat(flute.cargoSpaces()).isEqualTo(0)
	}

	@Test
	fun shouldTakeShieldsInterceptAndModuleCapacityFromItsHullOnly() {
		val flute = ship(Hull.FLUTE, Module.ARSENAL, Module.SAFE_JUMP)

		assertThat(flute.shields()).isEqualTo(3)
		assertThat(flute.intercept()).isEqualTo(3)
		assertThat(flute.moduleCapacity()).isEqualTo(3)
	}

	@Test
	fun shouldCountModulesUsedWithoutSafeJump() {
		assertThat(ship(Hull.FLUTE, Module.ARSENAL, Module.CARGO, Module.SAFE_JUMP).modulesUsed()).isEqualTo(2)
	}

	@Test
	fun shouldAddTheHullsBuiltInCargoToItsCargoModules() {
		assertThat(ship(Hull.MONARCH, Module.CARGO, Module.CARGO).cargoSpaces()).isEqualTo(4 + 2 + 2)
		assertThat(ship(Hull.CLARINET, Module.CARGO, Module.CARGO, Module.CARGO, Module.CARGO, Module.LIGHT_WEAPONS).cargoSpaces()).isEqualTo(8)
	}

	@Test
	fun shouldRefuseMoreModulesThanItsHullHolds() {
		assertThatThrownBy { ship(Hull.PICCOLO, Module.CARGO) }.isInstanceOf(InvalidFittingException::class.java)
	}

	@Test
	fun shouldKeepTenCargoSpacesForTheLegacyHullAndModules() {
		assertThat(Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT).cargoSpaces()).isEqualTo(Ship.CARGO_SPACES)
	}

	private fun ship(hull: Hull, vararg modules: Module) =
		Ship("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, emptyMap(), hull, modules.toList())
}
