package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R113, creator 2026-11-21: military modules from a card, fitted on a Planet; with no room, modules come off first, only as many as needed.
// Halcyon is a starting Clarinet (room for 5), its room full.
class ShipMilitaryModulesTest {

	private val full = emptyShip("Halcyon", GAMMA_LEPORIS, ShipLocation.PLANET).also { check(it.modulesUsed() == it.moduleCapacity()) }

	@Test
	fun shouldFitAModuleWhereOneCameOffToMakeRoom() {
		val fitted = full.withMilitaryModules(listOf(Module.BATTLE_COMM), listOf(Module.CARGO))

		assertThat(fitted.modules).containsExactlyInAnyOrderElementsOf(full.modules - Module.CARGO + Module.BATTLE_COMM)
		assertThat(fitted.modulesUsed()).isEqualTo(fitted.moduleCapacity())
	}

	@Test
	fun shouldSayHowManyMustComeOffWhenThereIsNoRoom() {
		assertThatThrownBy { full.withMilitaryModules(listOf(Module.BATTLE_COMM, Module.DOGFIGHT)) }
			.isInstanceOf(InvalidFittingException::class.java)
			.hasMessage("Halcyon has no room for these: remove 2 modules to fit them.")
	}

	@Test
	fun shouldRemoveOnlyWhatMakesRoom() {
		assertThatThrownBy { full.withMilitaryModules(listOf(Module.BATTLE_COMM), listOf(Module.CARGO, Module.CARGO)) }
			.isInstanceOf(InvalidFittingException::class.java)
			.hasMessage("Remove only what makes room: 1 module.")
		val roomy = full.withModuleRemoved(Module.CARGO)
		assertThatThrownBy { roomy.withMilitaryModules(listOf(Module.BATTLE_COMM), listOf(Module.CARGO)) }
			.isInstanceOf(InvalidFittingException::class.java)
			.hasMessage("Halcyon has room; nothing needs to come off.")
	}

	@Test
	fun shouldRefuseToRemoveAModuleItHasNotOrOneHoldingGoods() {
		assertThatThrownBy { full.withMilitaryModules(listOf(Module.BATTLE_COMM), listOf(Module.PASSENGER)) }
			.hasMessage("Halcyon has no Passenger module.")
		// 10 cargo spaces full of POLY: without a Cargo module only 8 are left (R65: goods are never lost to a refit)
		val loaded = full.copy(cargo = mapOf(Goods.POLY to full.cargoSpaces()))
		assertThatThrownBy { loaded.withMilitaryModules(listOf(Module.BATTLE_COMM), listOf(Module.CARGO)) }
			.hasMessageStartingWith("Halcyon carries")
	}
}
