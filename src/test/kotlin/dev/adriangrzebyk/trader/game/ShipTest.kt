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
				Ship.LEGACY_MODULES,
				Ship.STARTING_CREW_CLASS,
				null,
				0,
				emptyList()
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
				Ship.LEGACY_MODULES,
				Ship.STARTING_CREW_CLASS,
				null,
				0,
				emptyList()
			)

		assertThat(ship.cargoSpacesUsed()).isZero()
	}

	@Test
	fun shouldArriveInSpaceAtTheDestinationWhenJumping() {
		val ship = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)

		assertThat(ship.jumpTo(StarSystem.MU_HERCULIS)).isEqualTo(Ship.emptyAt("Wayfarer", StarSystem.MU_HERCULIS, ShipLocation.SPACE))
	}

	@Test
	fun shouldMoveIntoSpaceWhenJumpingToTheSystemItIsAlreadyAt() {
		// R69 (replaces S02-4)
		val ship = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)

		assertThat(ship.jumpTo(StarSystem.TAU_CETI)).isEqualTo(Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.SPACE))
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
				Ship.LEGACY_MODULES,
				Ship.STARTING_CREW_CLASS,
				null,
				0,
				emptyList()
			)

		assertThat(ship.unitsThatFit(Goods.ISO, 8)).isEqualTo(6)
		assertThat(ship.load(Goods.ISO, 8).cargo).isEqualTo(mapOf(Goods.POLY to 4, Goods.CHIPS to 12, Goods.ISO to 6))
	}

	@Test
	fun shouldLoadAnyAmountOfChipsAndFood() {
		val full =
			Ship(
				"Wayfarer",
				StarSystem.TAU_CETI,
				ShipLocation.PORT,
				mapOf(Goods.POLY to 10),
				Ship.LEGACY_HULL,
				Ship.LEGACY_MODULES,
				Ship.STARTING_CREW_CLASS,
				null,
				0,
				emptyList()
			)

		assertThat(full.unitsThatFit(Goods.FOOD, 30)).isEqualTo(30)
		assertThat(full.load(Goods.FOOD, 30).holding(Goods.FOOD)).isEqualTo(30)
		assertThat(full.load(Goods.ISO, 3).holding(Goods.ISO)).isZero()
	}

	@Test
	fun shouldUnloadWhatItSoldAndDropAGoodItNoLongerCarries() {
		val ship =
			Ship(
				"Wayfarer",
				StarSystem.TAU_CETI,
				ShipLocation.PORT,
				mapOf(Goods.ISO to 5, Goods.CHIPS to 2),
				Ship.LEGACY_HULL,
				Ship.LEGACY_MODULES,
				Ship.STARTING_CREW_CLASS,
				null,
				0,
				emptyList()
			)

		assertThat(ship.unload(Goods.ISO, 2).cargo).isEqualTo(mapOf(Goods.ISO to 3, Goods.CHIPS to 2))
		assertThat(ship.unload(Goods.ISO, 5).cargo).isEqualTo(mapOf(Goods.CHIPS to 2))
	}

	@Test
	fun shouldRefuseToUnloadMoreThanItHolds() {
		// R10
		val ship =
			Ship(
				"Wayfarer",
				StarSystem.TAU_CETI,
				ShipLocation.PORT,
				mapOf(Goods.ISO to 2),
				Ship.LEGACY_HULL,
				Ship.LEGACY_MODULES,
				Ship.STARTING_CREW_CLASS,
				null,
				0,
				emptyList()
			)

		assertThatThrownBy { ship.unload(Goods.ISO, 3) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Wayfarer has only 2 ISO.")
	}

	@Test
	fun shouldFitNothingInAnOverfullShip() {
		val overfull =
			Ship(
				"Wayfarer",
				StarSystem.TAU_CETI,
				ShipLocation.PORT,
				mapOf(Goods.POLY to 12),
				Ship.LEGACY_HULL,
				Ship.LEGACY_MODULES,
				Ship.STARTING_CREW_CLASS,
				null,
				0,
				emptyList()
			)

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
	fun shouldGroupRepeatedModulesWithACountInTheOrderTheyWereFitted() {
		// E07-S03-2
		assertThat(Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT).fittedModules().toList())
			.containsExactly(Module.CARGO to 4, Module.LIGHT_WEAPONS to 1, Module.SAFE_JUMP to 1)
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
		assertThat(legacy().cargoSpaces()).isEqualTo(10)
	}

	@Test
	fun shouldLoadNoPolyOrIsoOntoAShipWithoutCargoSpaces() {
		// R54: a Flute with no Cargo module holds nothing that takes space
		val flute = ship(Hull.FLUTE, Module.LIGHT_WEAPONS)

		assertThat(flute.load(Goods.POLY, 5).holding(Goods.POLY)).isZero()
		assertThat(flute.load(Goods.ISO, 5).cargo).isEmpty()
		assertThat(flute.freeSpacesFor(Goods.POLY)).isZero()
	}

	@Test
	fun shouldLoadChipsAndFoodOntoAShipWithoutCargoSpaces() {
		val flute = ship(Hull.FLUTE)

		assertThat(flute.load(Goods.CHIPS, 30).holding(Goods.CHIPS)).isEqualTo(30)
		assertThat(flute.load(Goods.FOOD, 7).holding(Goods.FOOD)).isEqualTo(7)
		assertThat(flute.freeSpacesFor(Goods.CHIPS)).isNull()
	}

	@Test
	fun shouldFillTheCargoSpacesItsModulesGiveAndLoseTheRest() {
		// R54, R16: a Monarch has 4 built in plus 2 for its Cargo module
		val monarch = ship(Hull.MONARCH, Module.CARGO)

		assertThat(monarch.load(Goods.POLY, 9).holding(Goods.POLY)).isEqualTo(6)
	}

	private fun legacy() = Ship(
		"Wayfarer",
		StarSystem.TAU_CETI,
		ShipLocation.PORT,
		emptyMap(),
		Ship.LEGACY_HULL,
		Ship.LEGACY_MODULES,
		Ship.STARTING_CREW_CLASS,
		null,
		0,
		emptyList()
	)

	private fun ship(hull: Hull, vararg modules: Module) = Ship(
		"Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT, emptyMap(), hull, modules.toList(), Ship.STARTING_CREW_CLASS,
		null, 0, emptyList()
	)

	// R68: base chance + crew (A +5, B +3, C +1, D +0) + Safe jump (+2).
	@Test
	fun shouldAddTheCrewAndSafeJumpModifiersToTheRoutesBaseChance() {
		val starting = Ship.emptyAt("Wayfarer", StarSystem.GAMMA_LEPORIS, ShipLocation.PORT)
		val withoutSafeJump = starting.withModuleRemoved(Module.SAFE_JUMP)

		assertThat(starting.jumpChanceTo(StarSystem.BETA_HYDRI)).isEqualTo(JumpChance(7 + 3 + 2))
		assertThat(withoutSafeJump.jumpChanceTo(StarSystem.BETA_HYDRI)).isEqualTo(JumpChance(7 + 3))
		assertThat(crewed(CrewClass.A).jumpChanceTo(StarSystem.MU_HERCULIS)).isEqualTo(JumpChance(2 + 5))
		assertThat(crewed(CrewClass.C).jumpChanceTo(StarSystem.MU_HERCULIS)).isEqualTo(JumpChance(2 + 1))
		assertThat(crewed(CrewClass.D).jumpChanceTo(StarSystem.MU_HERCULIS)).isEqualTo(JumpChance(2))
	}

	@Test
	fun shouldGiveAChanceForEveryOtherSystemOnly() {
		val chances = Ship.emptyAt("Wayfarer", StarSystem.GAMMA_LEPORIS, ShipLocation.PORT).jumpChances()

		assertThat(chances.keys).containsExactlyElementsOf(StarSystem.entries - StarSystem.GAMMA_LEPORIS)
	}

	private fun crewed(crewClass: CrewClass) = Ship(
		"Kestrel", StarSystem.GAMMA_LEPORIS, ShipLocation.PORT, emptyMap(), Hull.CLARINET,
		listOf(
			Module.CARGO
		),
		crewClass, null, 0, emptyList()
	)
}
