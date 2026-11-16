package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R58, E08-S02: moving goods between a ship in port and the player's warehouse at the same system.
class PlayerMoveCargoTest {

	/** Wayfarer is in port at Tau Ceti with 8 cargo spaces (R55), carrying 3 ISO and 5 CHIPS. */
	private val wayfarer = Ship.emptyAt("Wayfarer", TAU_CETI, ShipLocation.PORT).copy(cargo = mapOf(Goods.ISO to 3, Goods.CHIPS to 5))
	private val lantern = Ship.emptyAt("Lantern", MU_HERCULIS, ShipLocation.PORT)

	/** One block at Tau Ceti: 10 cargo spaces, holding 6 ISO and 2 FOOD. */
	private val warehouse = Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 6, Goods.FOOD to 2))

	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
		.startWith(300, listOf(wayfarer, lantern)).copy(warehouses = listOf(warehouse))

	@Test
	fun shouldMoveGoodsFromTheShipIntoTheWarehouse() {
		val moved = player.moveCargo("Wayfarer", Goods.ISO, 2, CargoDestination.WAREHOUSE)

		assertThat(moved.ship("Wayfarer")!!.cargo).isEqualTo(mapOf(Goods.ISO to 1, Goods.CHIPS to 5))
		assertThat(moved.warehouseAt(TAU_CETI)!!.cargo).isEqualTo(mapOf(Goods.ISO to 8, Goods.FOOD to 2))
	}

	@Test
	fun shouldMoveGoodsFromTheWarehouseIntoTheShip() {
		// the creator's case: what a factory made last turn is loaded to be sold elsewhere
		val moved = player.moveCargo("Wayfarer", Goods.ISO, 5, CargoDestination.SHIP)

		assertThat(moved.ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(8)
		assertThat(moved.warehouseAt(TAU_CETI)!!.holding(Goods.ISO)).isEqualTo(1)
	}

	@Test
	fun shouldKeepWhatThePlayerHoldsAtTheSystemAndTheirMoney() {
		// R49: a trade is capped by ships in port plus the warehouse there, so a move can't change what they may trade
		val moved = player.moveCargo("Wayfarer", Goods.ISO, 3, CargoDestination.WAREHOUSE)

		assertThat(moved.holdingAt(TAU_CETI, Goods.ISO)).isEqualTo(player.holdingAt(TAU_CETI, Goods.ISO))
		assertThat(moved.money).isEqualTo(player.money)
	}

	@Test
	fun shouldEmptyTheSourceWhenEverythingMoves() {
		val moved = player.moveCargo("Wayfarer", Goods.ISO, 3, CargoDestination.WAREHOUSE)

		assertThat(moved.ship("Wayfarer")!!.cargo).isEqualTo(mapOf(Goods.CHIPS to 5))
	}

	@Test
	fun shouldLeaveOtherShipsAndWarehousesAlone() {
		val elsewhere = Warehouse(MU_HERCULIS, 1, mapOf(Goods.POLY to 4))
		val moved = player.copy(warehouses = listOf(warehouse, elsewhere)).moveCargo("Wayfarer", Goods.ISO, 1, CargoDestination.WAREHOUSE)

		assertThat(moved.ship("Lantern")).isEqualTo(lantern)
		assertThat(moved.warehouseAt(MU_HERCULIS)).isEqualTo(elsewhere)
	}

	@Test
	fun shouldAlwaysFitChipsAndFoodWhichTakeNoCargoSpace() {
		// R42: the ship is full of ISO, and FOOD still loads
		val full = player.copy(ships = listOf(wayfarer.copy(cargo = mapOf(Goods.ISO to 8)), lantern))

		val moved = full.moveCargo("Wayfarer", Goods.FOOD, 2, CargoDestination.SHIP)

		assertThat(moved.ship("Wayfarer")!!.holding(Goods.FOOD)).isEqualTo(2)
	}

	@Test
	fun shouldRefuseMoreThanTheShipHolds() {
		assertThatThrownBy { player.moveCargo("Wayfarer", Goods.ISO, 4, CargoDestination.WAREHOUSE) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Wayfarer has only 3 ISO.")
	}

	@Test
	fun shouldRefuseMoreThanTheWarehouseHolds() {
		assertThatThrownBy { player.moveCargo("Wayfarer", Goods.POLY, 1, CargoDestination.SHIP) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Your warehouse at Tau Ceti has only 0 POLY.")
	}

	@Test
	fun shouldRefuseRatherThanLoseGoodsThatDoNotFitInTheShip() {
		// 8 spaces, 3 used: 5 free, and the warehouse holds 6 ISO
		assertThatThrownBy { player.moveCargo("Wayfarer", Goods.ISO, 6, CargoDestination.SHIP) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Wayfarer has room for only 5 more ISO.")
	}

	@Test
	fun shouldRefuseRatherThanLoseGoodsThatDoNotFitInTheWarehouse() {
		// 10 spaces, 9 used: 1 free
		val nearlyFull = player.copy(warehouses = listOf(Warehouse(TAU_CETI, 1, mapOf(Goods.POLY to 9))))

		assertThatThrownBy { nearlyFull.moveCargo("Wayfarer", Goods.ISO, 2, CargoDestination.WAREHOUSE) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Your warehouse at Tau Ceti has room for only 1 more ISO.")
	}

	@Test
	fun shouldRefuseFewerThanOneUnit() {
		assertThatThrownBy { player.moveCargo("Wayfarer", Goods.ISO, 0, CargoDestination.WAREHOUSE) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Enter a whole number of units, 1 or more.")
	}

	@Test
	fun shouldRefuseAShipThatIsNotThePlayers() {
		assertThatThrownBy { player.moveCargo("Meridian", Goods.ISO, 1, CargoDestination.WAREHOUSE) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Choose one of your ships.")
		assertThatThrownBy { player.moveCargo(null, Goods.ISO, 1, CargoDestination.WAREHOUSE) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
	}

	@Test
	fun shouldRefuseAShipThatIsNotInPort() {
		for (location in listOf(ShipLocation.SPACE, ShipLocation.PLANET)) {
			val away = player.copy(ships = listOf(wayfarer.copy(location = location), lantern))

			assertThatThrownBy { away.moveCargo("Wayfarer", Goods.ISO, 1, CargoDestination.WAREHOUSE) }
				.isInstanceOf(InvalidCargoMoveException::class.java)
				.hasMessage("Wayfarer isn't in port. Goods move only between a ship in port and your warehouse there.")
		}
	}

	@Test
	fun shouldRefuseWithNoWarehouseAtTheShipsSystem() {
		// Lantern is in port at Mu Herculis, where the player has no warehouse: Tau Ceti's doesn't count
		assertThatThrownBy { player.moveCargo("Lantern", Goods.ISO, 1, CargoDestination.SHIP) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("You have no warehouse at Mu Herculis.")
	}
}
