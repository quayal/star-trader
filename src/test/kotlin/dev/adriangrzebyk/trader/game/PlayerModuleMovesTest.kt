package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R122: legal modules stored in a warehouse; R123: military modules moved between ships in a Port with legal system 1–2.
// Ada has Wayfarer (a starting Clarinet, room full, with a Dogfight instead of a Cargo) and Lantern (one Cargo off, so
// room for one) in Mu Herculis Port (legal system 1), and a one-block warehouse there.
class PlayerModuleMovesTest {

	private fun at(system: StarSystem): Player {
		val wayfarer = emptyShip("Wayfarer", system, ShipLocation.PORT).let {
			it.withModuleTakenOff(Module.CARGO).withModuleFitted(Module.DOGFIGHT)
		}
		val lantern = emptyShip("Lantern", system, ShipLocation.PORT).withModuleTakenOff(Module.CARGO)
		return Player.newcomer("ada@example.com", "Ada", "Orion Freight")
			.copy(ships = listOf(wayfarer, lantern), warehouses = listOf(Warehouse(system, 1, emptyMap())))
	}

	private val ada = at(MU_HERCULIS)

	@Test
	fun shouldStoreALegalModuleAndFitItBackForFree() {
		val stored = ada.storeModule("Wayfarer", Module.LIGHT_WEAPONS)

		assertThat(stored.money).isEqualTo(ada.money)
		assertThat(stored.ship("Wayfarer")!!.modules).doesNotContain(Module.LIGHT_WEAPONS)
		assertThat(stored.warehouseAt(MU_HERCULIS)!!.modules).containsExactly(Module.LIGHT_WEAPONS)
		val fitted = stored.fitStoredModule("Lantern", Module.LIGHT_WEAPONS)
		assertThat(fitted.money).isEqualTo(ada.money)
		assertThat(fitted.ship("Lantern")!!.modules.count { it == Module.LIGHT_WEAPONS }).isEqualTo(2)
		assertThat(fitted.warehouseAt(MU_HERCULIS)!!.modules).isEmpty()
	}

	@Test
	fun shouldRefuseToStoreWithoutAWarehouseOrOutOfPort() {
		val noWarehouse = ada.copy(warehouses = emptyList())
		assertThatThrownBy { noWarehouse.storeModule("Wayfarer", Module.CARGO) }.hasMessage("You have no warehouse at Mu Herculis.")
		val inSpace = ada.copy(ships = ada.ships.map { it.copy(location = ShipLocation.SPACE) })
		assertThatThrownBy { inSpace.storeModule("Wayfarer", Module.CARGO) }.hasMessage("Wayfarer must be in port to move modules.")
		assertThatThrownBy { ada.storeModule("Wayfarer", Module.DOGFIGHT) }
			.hasMessage("Dogfight is military: it can't be stored in a warehouse.")
	}

	@Test
	fun shouldMoveAMilitaryModuleBetweenShipsInALawlessPort() {
		val moved = ada.moveMilitaryModule("Wayfarer", "Lantern", Module.DOGFIGHT)

		assertThat(moved.money).isEqualTo(ada.money)
		assertThat(moved.ship("Wayfarer")!!.modules).doesNotContain(Module.DOGFIGHT)
		assertThat(moved.ship("Lantern")!!.modules).contains(Module.DOGFIGHT)
	}

	@Test
	fun shouldRefuseAMoveInALawfulPortOrOfALegalModule() {
		// Beta Hydri's legal system is 4 (R31)
		assertThatThrownBy { at(BETA_HYDRI).moveMilitaryModule("Wayfarer", "Lantern", Module.DOGFIGHT) }
			.hasMessage("Military modules change ships only in a Port with legal system 1 or 2; Beta Hydri's is 4.")
		assertThatThrownBy { ada.moveMilitaryModule("Wayfarer", "Lantern", Module.LIGHT_WEAPONS) }
			.hasMessage("Light weapons is legal: move it through your warehouse.")
		assertThatThrownBy { ada.moveMilitaryModule("Wayfarer", "Wayfarer", Module.DOGFIGHT) }.hasMessage("Choose two different ships.")
		val apart = ada.copy(ships = ada.ships.map { if (it.name == "Lantern") it.copy(system = TAU_CETI) else it })
		assertThatThrownBy { apart.moveMilitaryModule("Wayfarer", "Lantern", Module.DOGFIGHT) }
			.hasMessage("Wayfarer and Lantern aren't in the same port.")
		assertThatThrownBy { ada.moveMilitaryModule("Lantern", "Wayfarer", Module.DOGFIGHT) }.hasMessage("Lantern has no Dogfight module.")
	}

	@Test
	fun shouldListWhatEachShipCanDoWithItsModules() {
		val moves = ada.moduleMoves("Wayfarer")

		assertThat(moves.storable).containsExactly(Module.CARGO, Module.LIGHT_WEAPONS, Module.SAFE_JUMP)
		assertThat(moves.stored).isEmpty()
		assertThat(moves.transfers).containsExactly(ModuleTransfer(Module.DOGFIGHT, "Lantern"))
		// Wayfarer's room is full, so a stored module doesn't fit it; Lantern has room
		val stored = ada.storeModule("Lantern", Module.CARGO)
		assertThat(stored.moduleMoves("Wayfarer").stored).isEmpty()
		assertThat(stored.moduleMoves("Lantern").stored).containsExactly(Module.CARGO)
		assertThat(at(BETA_HYDRI).moduleMoves("Wayfarer").transfers).isEmpty()
		assertThat(ada.copy(ships = ada.ships.map { it.copy(location = ShipLocation.SPACE) }).moduleMoves("Wayfarer")).isEqualTo(ModuleMoves.NONE)
	}
}
