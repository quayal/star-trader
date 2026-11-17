package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.battle.InvalidBattleException
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E10: what a ship brings to a battle and what hits do to it (R70–R76, R79).
class ShipBattleTest {

	// A starting ship: Clarinet (weapons 3, shields 2, intercept 2), 4 Cargo, 1 Light weapons 3 (5), 1 Safe jump; class B.
	private val wayfarer = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.SPACE)

	@Test
	fun shouldFireWithTheHullAndWorkingModules() {
		assertThat(wayfarer.firepower(rockets = false)).isEqualTo(6)
		assertThat(wayfarer.firepower(rockets = true)).isEqualTo(8)
		assertThat(wayfarer.copy(damagedModules = listOf(Module.LIGHT_WEAPONS)).firepower(rockets = true)).isEqualTo(3)
	}

	@Test
	fun shouldUseRocketValuesOnlyForModulesThatHaveThem() {
		// R75: Battle comm has no rocket value, so it fires its 6 either way.
		val armed = wayfarer.copy(modules = listOf(Module.BATTLE_COMM, Module.HEAVY_WEAPONS))

		assertThat(armed.firepower(rockets = false)).isEqualTo(3 + 6 + 7)
		assertThat(armed.firepower(rockets = true)).isEqualTo(3 + 6 + 10)
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
		assertThat(wayfarer.copy(crewClass = CrewClass.D).interceptLog(4)).isEqualTo("Wayfarer (2 + d6 4 − 1 = 5)")
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
		val damaged = wayfarer.copy(hullDamage = 2, damagedModules = listOf(Module.CARGO, Module.LIGHT_WEAPONS))

		assertThat(damaged.repairCost()).isEqualTo(34)
		assertThat(damaged.repaired()).isEqualTo(wayfarer)
		assertThat(wayfarer.copy(damagedModules = listOf(Module.CARGO)).repairCost()).isEqualTo(4)
		assertThatThrownBy { wayfarer.repaired() }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Wayfarer has no damage to repair.")
	}

	@Test
	fun shouldRemoveAWorkingModuleBeforeADamagedOne() {
		val port = wayfarer.copy(system = StarSystem.TAU_CETI, location = ShipLocation.PORT, damagedModules = listOf(Module.CARGO))

		assertThat(port.withModuleRemoved(Module.CARGO).damagedModules).containsExactly(Module.CARGO)
		val oneDamaged = port.copy(modules = listOf(Module.CARGO), damagedModules = listOf(Module.CARGO))
		assertThat(oneDamaged.withModuleRemoved(Module.CARGO).damagedModules).isEmpty()
	}
}
