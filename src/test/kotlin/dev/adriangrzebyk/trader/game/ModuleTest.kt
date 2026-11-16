package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ModuleTest {

	@Test
	fun shouldHoldTheEightModulesOfTheCreatorsTable() {
		assertThat(Module.entries.map { it.displayName }).containsExactly(
			"Arsenal",
			"Safe jump",
			"Battle comm",
			"Cargo",
			"Heavy weapons",
			"Dogfight",
			"Light weapons",
			"Passenger"
		)
	}

	@Test
	fun shouldCarryEveryNumberOfAModuleRow() {
		val arsenal = Module.ARSENAL

		assertThat(listOf(arsenal.weapons, arsenal.rocketWeapons, arsenal.crew, arsenal.cargoSpaces, arsenal.passengers, arsenal.price))
			.containsExactly(7, 14, 1, 0, 0, 100)
		assertThat(arsenal.military).isTrue()
	}

	@Test
	fun shouldTakeRocketWeaponsFromTheBracketsAndZeroWithoutThem() {
		assertThat(Module.entries.associate { it.displayName to it.rocketWeapons }).containsEntry("Heavy weapons", 10)
			.containsEntry("Dogfight", 7).containsEntry("Light weapons", 5).containsEntry("Battle comm", 0).containsEntry("Cargo", 0)
	}

	@Test
	fun shouldFlagTheFourMilitaryModules() {
		assertThat(Module.entries.filter { it.military })
			.containsExactly(Module.ARSENAL, Module.BATTLE_COMM, Module.HEAVY_WEAPONS, Module.DOGFIGHT)
	}

	@Test
	fun shouldGiveACargoModuleTwoSpacesAndAPassengerModuleTwoPassengers() {
		assertThat(Module.CARGO.cargoSpaces).isEqualTo(2)
		assertThat(Module.PASSENGER.passengers).isEqualTo(2)
	}

	@Test
	fun shouldTakeNoModuleCapacityForSafeJumpOnly() {
		assertThat(Module.entries.filter { !it.takesModuleCapacity }).containsExactly(Module.SAFE_JUMP)
	}
}
