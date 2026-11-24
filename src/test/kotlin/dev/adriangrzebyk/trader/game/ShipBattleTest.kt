package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.battle.InvalidBattleException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E10: what a ship brings to a battle and what hits do to it (R70–R76, R79).
class ShipBattleTest {

	// A starting ship: Clarinet (weapons 3, shields 2, intercept 2), 4 Cargo, 1 Light weapons 3 (5), 1 Safe jump; class B.
	private val wayfarer = emptyShip("Wayfarer", StarSystem.TAU_CETI, ShipLocation.SPACE)

	@Test
	fun shouldFireWithTheHullAndWorkingModules() {
		assertThat(wayfarer.firepower(rockets = false)).isEqualTo(6)
		assertThat(wayfarer.copy(damagedModules = listOf(Module.LIGHT_WEAPONS)).firepower(rockets = false)).isEqualTo(3)
	}

	@Test
	fun shouldFireRocketsWithTheWorkingModulesRocketValuesOnly() {
		// R75 (creator, 2026-11-17): the hull's 3 and Battle comm's 6 add nothing to a rocket salvo.
		val armed = wayfarer.copy(modules = listOf(Module.BATTLE_COMM, Module.HEAVY_WEAPONS, Module.LIGHT_WEAPONS))

		assertThat(wayfarer.firepower(rockets = true)).isEqualTo(5)
		assertThat(armed.firepower(rockets = false)).isEqualTo(3 + 6 + 7 + 3)
		assertThat(armed.firepower(rockets = true)).isEqualTo(10 + 5)
		assertThat(armed.copy(damagedModules = listOf(Module.HEAVY_WEAPONS)).firepower(rockets = true)).isEqualTo(5)
	}

	@Test
	fun shouldRefuseRocketsFromAShipWithNoWorkingRocketModule() {
		// R75
		wayfarer.checkCanFireRockets()
		assertThatThrownBy { wayfarer.copy(damagedModules = listOf(Module.LIGHT_WEAPONS)).checkCanFireRockets() }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Wayfarer has no working rocket module, so it can't fire rockets.")
	}

	@Test
	fun shouldAddCrewClassAndAWorkingBattleCommToInterception() {
		// R71
		assertThat(wayfarer.interceptTotal(4)).isEqualTo(6)
		assertThat(wayfarer.copy(crewClass = CrewClass.A).interceptTotal(4)).isEqualTo(7)
		assertThat(wayfarer.copy(crewClass = CrewClass.D).interceptTotal(4)).isEqualTo(5)
		val comm = wayfarer.copy(modules = listOf(Module.BATTLE_COMM, Module.BATTLE_COMM))
		assertThat(comm.interceptTotal(4)).isEqualTo(7)
		assertThat(comm.copy(damagedModules = listOf(Module.BATTLE_COMM, Module.BATTLE_COMM)).interceptTotal(4)).isEqualTo(6)
		assertThat(wayfarer.copy(crewClass = CrewClass.D).interceptLog(4, 0)).isEqualTo("Wayfarer (2 + d6 4 − 1 = 5)")
	}

	@Test
	fun shouldDamageAModuleWithOneHitAndDestroyItWithTwo() {
		val hit = wayfarer.takeHits(3, HitPlacement(0, listOf(ModuleHit(Module.CARGO, false, 1), ModuleHit(Module.CARGO, false, 2)), emptyMap()))

		assertThat(hit.modules.count { it == Module.CARGO }).isEqualTo(3)
		assertThat(hit.damagedModules).containsExactly(Module.CARGO)
		assertThat(hit.cargoSpaces()).isEqualTo(4)
		assertThat(hit.isDestroyed()).isFalse()
	}

	@Test
	fun shouldDestroyADamagedModuleWithOneHit() {
		val damaged = wayfarer.copy(damagedModules = listOf(Module.LIGHT_WEAPONS))

		val hit = damaged.takeHits(1, HitPlacement(0, listOf(ModuleHit(Module.LIGHT_WEAPONS, true, 1)), emptyMap()))

		assertThat(hit.modules).doesNotContain(Module.LIGHT_WEAPONS)
		assertThat(hit.damagedModules).isEmpty()
	}

	@Test
	fun shouldBeDestroyedWhenTheHullTakesOneHitMoreThanItsShields() {
		assertThat(wayfarer.takeHits(2, HitPlacement(2, emptyList(), emptyMap())).isDestroyed()).isFalse()
		assertThat(wayfarer.takeHits(3, HitPlacement(3, emptyList(), emptyMap())).isDestroyed()).isTrue()
	}

	@Test
	fun shouldRefuseAPlacementThatDoesNotAddUp() {
		assertThatThrownBy { wayfarer.takeHits(2, HitPlacement(1, emptyList(), emptyMap())) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Place exactly 2 hits on Wayfarer, not 1.")
	}

	@Test
	fun shouldRefuseHitsOnAModuleTheShipDoesNotHaveInThatCondition() {
		assertThatThrownBy { wayfarer.takeHits(1, HitPlacement(0, listOf(ModuleHit(Module.CARGO, true, 1)), emptyMap())) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Wayfarer has no more damaged Cargo modules to hit.")
		assertThatThrownBy { wayfarer.takeHits(2, HitPlacement(0, listOf(ModuleHit(Module.ARSENAL, false, 2)), emptyMap())) }
			.isInstanceOf(InvalidBattleException::class.java)
	}

	@Test
	fun shouldRefuseThreeHitsOnOneModule() {
		assertThatThrownBy { wayfarer.takeHits(3, HitPlacement(0, listOf(ModuleHit(Module.CARGO, false, 3)), emptyMap())) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("A module takes 1 or 2 hits, and a damaged one 1.")
	}

	@Test
	fun shouldMakeTheOwnerChooseTheCargoLostWhenCargoSpaceIsHit() {
		// 8 spaces, 7 used; losing two Cargo modules leaves 4, so 3 units go overboard.
		val loaded = wayfarer.copy(cargo = mapOf(Goods.POLY to 4, Goods.ISO to 3, Goods.FOOD to 5))
		val twoCargo = listOf(ModuleHit(Module.CARGO, false, 1), ModuleHit(Module.CARGO, false, 1))

		assertThatThrownBy { loaded.takeHits(2, HitPlacement(0, twoCargo, emptyMap())) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Wayfarer loses 3 units of POLY and ISO: choose which.")
		val hit = loaded.takeHits(2, HitPlacement(0, twoCargo, mapOf(Goods.POLY to 1, Goods.ISO to 2)))
		assertThat(hit.cargo).isEqualTo(mapOf(Goods.POLY to 3, Goods.ISO to 1, Goods.FOOD to 5))
	}

	@Test
	fun shouldRefuseAJettisonWhenNothingIsLost() {
		val loaded = wayfarer.copy(cargo = mapOf(Goods.POLY to 2))

		assertThatThrownBy { loaded.takeHits(1, HitPlacement(1, emptyList(), mapOf(Goods.POLY to 1))) }
			.isInstanceOf(InvalidBattleException::class.java)
			.hasMessage("Wayfarer keeps all its cargo.")
	}

	@Test
	fun shouldSurviveAsManyHitsAsItsHullAndModulesAbsorb() {
		// 2 on the hull + 2 for each of 6 working modules
		assertThat(wayfarer.hitsSurvivable()).isEqualTo(14)
		assertThat(wayfarer.copy(hullDamage = 1, damagedModules = listOf(Module.CARGO)).hitsSurvivable()).isEqualTo(12)
	}

	@Test
	fun shouldRepairEverythingForTheHullPriceAndEachModule() {
		// R79: Clarinet 20, Cargo 4, Light weapons 10
		val atShipyard = wayfarer.copy(system = StarSystem.TAU_CETI, location = ShipLocation.PORT)
		val damaged = atShipyard.copy(hullDamage = 2, damagedModules = listOf(Module.CARGO, Module.LIGHT_WEAPONS))

		assertThat(damaged.repairCost()).isEqualTo(34)
		assertThat(damaged.repairCostHere()).isEqualTo(34)
		assertThat(damaged.repaired()).isEqualTo(atShipyard)
		assertThat(atShipyard.copy(damagedModules = listOf(Module.CARGO)).repairCost()).isEqualTo(4)
		assertThatThrownBy { atShipyard.repaired() }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Wayfarer has no damage to repair.")
	}

	@Test
	fun shouldRepairModulesAnywhereButTheHullOnlyAtAShipyard() {
		// R133: Clarinet hull 20, Cargo 4, Light weapons 10; Gamma Leporis has no shipyard
		val away = wayfarer.copy(
			system = StarSystem.GAMMA_LEPORIS,
			location = ShipLocation.PORT,
			hullDamage = 1,
			damagedModules = listOf(Module.CARGO)
		)

		assertThat(away.repairCost()).isEqualTo(24)
		assertThat(away.repairCostHere()).isEqualTo(4)
		assertThat(away.canBeRepaired()).isTrue()
		assertThat(away.hullRepairNote()).isEqualTo("The hull is repaired at a shipyard: in port at Beta Hydri, Epsilon Eridani or Tau Ceti.")
		assertThat(away.repaired()).isEqualTo(away.copy(damagedModules = emptyList()))
		val inSpace = away.copy(location = ShipLocation.SPACE)
		assertThat(inSpace.repaired().damagedModules).isEmpty()
		assertThat(inSpace.repaired().hullDamage).isEqualTo(1)
	}

	@Test
	fun shouldRefuseToRepairOnlyAHullAwayFromAShipyard() {
		val away = wayfarer.copy(system = StarSystem.GAMMA_LEPORIS, location = ShipLocation.PORT, hullDamage = 1)

		assertThat(away.canBeRepaired()).isFalse()
		assertThatThrownBy { away.repaired() }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Wayfarer: The hull is repaired at a shipyard: in port at Beta Hydri, Epsilon Eridani or Tau Ceti.")
	}

	@Test
	fun shouldRepairAMilitaryHullOnlyAtTauCetisShipyard() {
		// R133: Beta Hydri and Epsilon Eridani have shipyards too, but their Ports refuse military ships (R56)
		val military = wayfarer.copy(hull = Hull.DAGGER, modules = emptyList(), location = ShipLocation.PORT, hullDamage = 1)

		assertThat(military.copy(system = StarSystem.TAU_CETI).canRepairHull()).isTrue()
		assertThat(military.copy(system = StarSystem.BETA_HYDRI).canRepairHull()).isFalse()
		assertThat(military.copy(system = StarSystem.EPSILON_ERIDANI).canRepairHull()).isFalse()
		assertThat(
			military.copy(system = StarSystem.BETA_HYDRI).hullRepairNote()
		).isEqualTo("A military hull is repaired only at the Tau Ceti shipyard.")
		assertThat(military.copy(system = StarSystem.TAU_CETI).hullRepairNote()).isNull()
		assertThat(military.copy(system = StarSystem.BETA_HYDRI, hull = Hull.CLARINET).canRepairHull()).isTrue()
	}

	@Test
	fun shouldRemoveAWorkingModuleBeforeADamagedOne() {
		val port = wayfarer.copy(system = StarSystem.TAU_CETI, location = ShipLocation.PORT, damagedModules = listOf(Module.CARGO))

		assertThat(port.withModuleRemoved(Module.CARGO).damagedModules).containsExactly(Module.CARGO)
		val oneDamaged = port.copy(modules = listOf(Module.CARGO), damagedModules = listOf(Module.CARGO))
		assertThat(oneDamaged.withModuleRemoved(Module.CARGO).damagedModules).isEmpty()
	}
}
