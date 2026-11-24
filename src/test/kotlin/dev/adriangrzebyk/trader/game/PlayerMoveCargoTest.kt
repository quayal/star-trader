package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R58, E08-S02: moving goods between a ship in port and the player's warehouse at the same system, or (revised
// 2026-11-17) between two of their ships in the same port.
class PlayerMoveCargoTest {

	/** Wayfarer is in port at Tau Ceti with 8 cargo spaces (R55), carrying 3 ISO and 5 CHIPS. */
	private val wayfarer = emptyShip("Wayfarer", TAU_CETI, ShipLocation.PORT).copy(cargo = mapOf(Goods.ISO to 3, Goods.CHIPS to 5))
	private val lantern = emptyShip("Lantern", MU_HERCULIS, ShipLocation.PORT)

	/** One block at Tau Ceti: 10 cargo spaces, holding 6 ISO and 2 FOOD. */
	private val warehouse = Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 6, Goods.FOOD to 2))

	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
		.startWith(300, listOf(wayfarer, lantern), 0).copy(warehouses = listOf(warehouse))

	@Test
	fun shouldMoveGoodsFromTheShipIntoTheWarehouse() {
		val moved = player.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 2)

		assertThat(moved.ship("Wayfarer")!!.cargo).isEqualTo(mapOf(Goods.ISO to 1, Goods.CHIPS to 5))
		assertThat(moved.warehouseAt(TAU_CETI)!!.cargo).isEqualTo(mapOf(Goods.ISO to 8, Goods.FOOD to 2))
	}

	@Test
	fun shouldMoveGoodsFromTheWarehouseIntoTheShip() {
		// the creator's case: what a factory made last turn is loaded to be sold elsewhere
		val moved = player.moveCargo(CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 5)

		assertThat(moved.ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(8)
		assertThat(moved.warehouseAt(TAU_CETI)!!.holding(Goods.ISO)).isEqualTo(1)
	}

	@Test
	fun shouldKeepWhatThePlayerHoldsAtTheSystemAndTheirMoney() {
		// R49: a trade is capped by ships in port plus the warehouse there, so a move can't change what they may trade
		val moved = player.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 3)

		assertThat(moved.holdingAt(TAU_CETI, Goods.ISO)).isEqualTo(player.holdingAt(TAU_CETI, Goods.ISO))
		assertThat(moved.money).isEqualTo(player.money)
	}

	@Test
	fun shouldEmptyTheSourceWhenEverythingMoves() {
		val moved = player.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 3)

		assertThat(moved.ship("Wayfarer")!!.cargo).isEqualTo(mapOf(Goods.CHIPS to 5))
	}

	@Test
	fun shouldLeaveOtherShipsAndWarehousesAlone() {
		val elsewhere = Warehouse(MU_HERCULIS, 1, mapOf(Goods.POLY to 4))
		val moved = player.copy(
			warehouses = listOf(warehouse, elsewhere)
		).moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 1)

		assertThat(moved.ship("Lantern")).isEqualTo(lantern)
		assertThat(moved.warehouseAt(MU_HERCULIS)).isEqualTo(elsewhere)
	}

	@Test
	fun shouldAlwaysFitChipsAndFoodWhichTakeNoCargoSpace() {
		// R42: the ship is full of ISO, and FOOD still loads
		val full = player.copy(ships = listOf(wayfarer.copy(cargo = mapOf(Goods.ISO to 8)), lantern))

		val moved = full.moveCargo(CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.FOOD, 2)

		assertThat(moved.ship("Wayfarer")!!.holding(Goods.FOOD)).isEqualTo(2)
	}

	@Test
	fun shouldRefuseMoreThanTheShipHolds() {
		assertThatThrownBy { player.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 4) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Wayfarer has only 3 ISO.")
	}

	@Test
	fun shouldRefuseMoreThanTheWarehouseHolds() {
		assertThatThrownBy { player.moveCargo(CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.POLY, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Your warehouse at Tau Ceti has only 0 POLY.")
	}

	@Test
	fun shouldRefuseRatherThanLoseGoodsThatDoNotFitInTheShip() {
		// 8 spaces, 3 used: 5 free, and the warehouse holds 6 ISO
		assertThatThrownBy { player.moveCargo(CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 6) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Wayfarer has room for only 5 more ISO.")
	}

	@Test
	fun shouldRefuseRatherThanLoseGoodsThatDoNotFitInTheWarehouse() {
		// 10 spaces, 9 used: 1 free
		val nearlyFull = player.copy(warehouses = listOf(Warehouse(TAU_CETI, 1, mapOf(Goods.POLY to 9))))

		assertThatThrownBy { nearlyFull.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 2) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Your warehouse at Tau Ceti has room for only 1 more ISO.")
	}

	@Test
	fun shouldRefuseFewerThanOneUnit() {
		assertThatThrownBy { player.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 0) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Enter a whole number of units, 1 or more.")
	}

	@Test
	fun shouldRefuseAShipThatIsNotThePlayers() {
		assertThatThrownBy { player.moveCargo(CargoHolder.Ship("Meridian"), CargoHolder.Warehouse, Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Choose one of your ships.")
		assertThatThrownBy { player.moveCargo(CargoHolder.Ship(null), CargoHolder.Warehouse, Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
	}

	@Test
	fun shouldRefuseAShipThatIsNotInPort() {
		for (location in listOf(ShipLocation.SPACE, ShipLocation.PLANET)) {
			val away = player.copy(ships = listOf(wayfarer.copy(location = location), lantern))

			assertThatThrownBy { away.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Warehouse, Goods.ISO, 1) }
				.isInstanceOf(InvalidCargoMoveException::class.java)
				.hasMessage("Wayfarer isn't in port. Goods move only between your ships and warehouse in the same port.")
		}
	}

	@Test
	fun shouldRefuseWithNoWarehouseAtTheShipsSystem() {
		// Lantern is in port at Mu Herculis, where the player has no warehouse: Tau Ceti's doesn't count
		assertThatThrownBy { player.moveCargo(CargoHolder.Warehouse, CargoHolder.Ship("Lantern"), Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("You have no warehouse at Mu Herculis.")
	}

	/** Comet is in port at Tau Ceti beside Wayfarer, empty, with 8 cargo spaces. */
	private val comet = emptyShip("Comet", TAU_CETI, ShipLocation.PORT)
	private val fleet = player.copy(ships = listOf(wayfarer, lantern, comet))

	@Test
	fun shouldMoveGoodsBetweenTwoShipsInTheSamePort() {
		val moved = fleet.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Ship("Comet"), Goods.ISO, 2)

		assertThat(moved.ship("Wayfarer")!!.cargo).isEqualTo(mapOf(Goods.ISO to 1, Goods.CHIPS to 5))
		assertThat(moved.ship("Comet")!!.cargo).isEqualTo(mapOf(Goods.ISO to 2))
		assertThat(moved.warehouseAt(TAU_CETI)).isEqualTo(warehouse)
	}

	@Test
	fun shouldMoveGoodsBetweenTwoShipsWhereThePlayerHasNoWarehouse() {
		val noWarehouse = fleet.copy(warehouses = emptyList())

		val moved = noWarehouse.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Ship("Comet"), Goods.CHIPS, 5)

		assertThat(moved.ship("Comet")!!.holding(Goods.CHIPS)).isEqualTo(5)
		assertThat(moved.ship("Wayfarer")!!.holding(Goods.CHIPS)).isEqualTo(0)
	}

	@Test
	fun shouldRefuseShipsInDifferentPorts() {
		assertThatThrownBy { fleet.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Ship("Lantern"), Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Wayfarer and Lantern aren't in the same port.")
	}

	@Test
	fun shouldRefuseTheSameShipAtBothEnds() {
		assertThatThrownBy { fleet.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Ship("Wayfarer"), Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Choose two different ships.")
	}

	@Test
	fun shouldRefuseAWarehouseAtBothEnds() {
		assertThatThrownBy { fleet.moveCargo(CargoHolder.Warehouse, CargoHolder.Warehouse, Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Choose one of your ships.")
	}

	@Test
	fun shouldRefuseAReceivingShipThatIsNotInPort() {
		val away = fleet.copy(ships = listOf(wayfarer, lantern, comet.copy(location = ShipLocation.SPACE)))

		assertThatThrownBy { away.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Ship("Comet"), Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Comet isn't in port. Goods move only between your ships and warehouse in the same port.")
	}

	@Test
	fun shouldRefuseRatherThanLoseGoodsThatDoNotFitInTheOtherShip() {
		val fullComet = fleet.copy(ships = listOf(wayfarer, lantern, comet.copy(cargo = mapOf(Goods.POLY to 7))))

		assertThatThrownBy { fullComet.moveCargo(CargoHolder.Ship("Wayfarer"), CargoHolder.Ship("Comet"), Goods.ISO, 2) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Comet has room for only 1 more ISO.")
	}
}
