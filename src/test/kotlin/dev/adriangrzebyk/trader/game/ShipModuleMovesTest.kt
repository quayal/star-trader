package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.game.news.TipOff
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R122, R123: a module taken off one holder and fitted to another, free, its crew coming along. Halcyon is a starting
// Clarinet (4 Cargo, Light weapons, Safe jump), its room full.
class ShipModuleMovesTest {

	private val full = emptyShip("Halcyon", MU_HERCULIS, ShipLocation.PORT)

	@Test
	fun shouldTakeOffAWorkingModuleWithNothingPaid() {
		val off = full.withModuleTakenOff(Module.LIGHT_WEAPONS)

		assertThat(off.modules).doesNotContain(Module.LIGHT_WEAPONS)
		assertThat(off.crewClass).isEqualTo(full.crewClass)
	}

	@Test
	fun shouldRefuseADamagedModuleOrOneItHasNot() {
		val damaged = full.copy(damagedModules = listOf(Module.LIGHT_WEAPONS))

		assertThat(damaged.canTakeOff(Module.LIGHT_WEAPONS)).isFalse()
		assertThatThrownBy { damaged.withModuleTakenOff(Module.LIGHT_WEAPONS) }
			.hasMessage("Halcyon's Light weapons module is damaged: repair it first.")
		assertThatThrownBy { full.withModuleTakenOff(Module.DOGFIGHT) }.hasMessage("Halcyon has no Dogfight module.")
	}

	@Test
	fun shouldRefuseToLeaveTooLittleRoomForWhatIsAboard() {
		val loaded = full.copy(cargo = mapOf(Goods.POLY to full.cargoSpaces()))

		assertThat(loaded.canTakeOff(Module.CARGO)).isFalse()
		assertThatThrownBy { loaded.withModuleTakenOff(Module.CARGO) }.hasMessageStartingWith("Halcyon carries")
		val seated = full.withModuleTakenOff(Module.CARGO).withModuleFitted(Module.PASSENGER).withDelivery(TipOff.O22, Module.PASSENGER)
		assertThatThrownBy { seated.withModuleTakenOff(Module.PASSENGER) }
			.hasMessage("Halcyon's passenger seats are taken: without that module it would have too few.")
	}

	@Test
	fun shouldFitAModuleOnlyWhereTheHullHasRoom() {
		assertThat(full.hasRoomFor(Module.DOGFIGHT)).isFalse()
		assertThatThrownBy { full.withModuleFitted(Module.DOGFIGHT) }.isInstanceOf(InvalidFittingException::class.java)
		val roomy = full.withModuleTakenOff(Module.CARGO)
		assertThat(roomy.hasRoomFor(Module.DOGFIGHT)).isTrue()
		assertThat(roomy.withModuleFitted(Module.DOGFIGHT).modules).contains(Module.DOGFIGHT)
	}

	@Test
	fun shouldDeliverOnlyInTheDestinationPort() {
		// R121: O22's animals go from Mu Herculis to Epsilon Eridani
		val carrying = full.withModuleTakenOff(Module.CARGO).withModuleFitted(Module.PASSENGER).withDelivery(TipOff.O22, Module.PASSENGER)

		assertThat(carrying.deliveryDestination()).isEqualTo(EPSILON_ERIDANI)
		assertThat(carrying.canDeliver()).isFalse()
		assertThat(carrying.copy(system = EPSILON_ERIDANI).canDeliver()).isTrue()
		assertThat(carrying.copy(system = EPSILON_ERIDANI, location = ShipLocation.SPACE).canDeliver()).isFalse()
	}
}
