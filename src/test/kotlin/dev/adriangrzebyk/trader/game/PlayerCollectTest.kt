package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R47, E06-S03: collecting what the player's factories made into their ships in port and their warehouses.
class PlayerCollectTest {

	private val wayfarer = Ship.emptyAt("Wayfarer", TAU_CETI, ShipLocation.PORT)
	private val lantern = Ship.emptyAt("Lantern", MU_HERCULIS, ShipLocation.PORT)
	private val isoAtTau = Factory(TAU_CETI, Goods.ISO, 6)
	private val chipsAtMu = Factory(MU_HERCULIS, Goods.CHIPS, 8)
	private val tauWarehouse = Warehouse(TAU_CETI, 1, emptyMap())

	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
		.startWith(300, listOf(wayfarer, lantern), 0)
		.copy(factories = listOf(isoAtTau, chipsAtMu), warehouses = listOf(tauWarehouse))

	@Test
	fun shouldStoreTheOutputInTheChosenShipAndWarehouse() {
		val collected = player.collect(
			listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 4), OutputPlacement(TAU_CETI, Goods.ISO, null, 2))
		)

		assertThat(collected.ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(4)
		assertThat(collected.warehouseAt(TAU_CETI)!!.holding(Goods.ISO)).isEqualTo(2)
	}

	@Test
	fun shouldLoseWhateverIsNotPlaced() {
		// R47: output left unplaced is lost, so nothing but the placed units is stored
		val collected = player.collect(listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 1)))

		assertThat(collected.ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(1)
		assertThat(collected.warehouseAt(TAU_CETI)!!.cargo).isEmpty()
	}

	@Test
	fun shouldLoseEverythingWhenNothingIsPlaced() {
		assertThat(player.collect(emptyList())).isEqualTo(player)
	}

	@Test
	fun shouldLosePolyAndIsoBeyondTheFreeCargoSpace() {
		// R16, E06-S03-3: the ship has 8 spaces and already holds 6 POLY, so only 2 of the 6 ISO fit
		val loaded = player.copy(ships = listOf(wayfarer.copy(cargo = mapOf(Goods.POLY to 6)), lantern))

		val collected = loaded.collect(listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 6)))

		assertThat(collected.ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(2)
	}

	@Test
	fun shouldStoreChipsAndFoodWithoutUsingSpace() {
		// R42: CHIPS and FOOD take no space; the Mu ship is full of POLY and still takes all 8 CHIPS
		val full = player.copy(ships = listOf(wayfarer, lantern.copy(cargo = mapOf(Goods.POLY to 10))))

		val collected = full.collect(listOf(OutputPlacement(MU_HERCULIS, Goods.CHIPS, "Lantern", 8)))

		assertThat(collected.ship("Lantern")!!.holding(Goods.CHIPS)).isEqualTo(8)
	}

	@Test
	fun shouldRefuseMorethanTheFactoryMakes() {
		assertThatThrownBy {
			player.collect(listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 4), OutputPlacement(TAU_CETI, Goods.ISO, null, 3)))
		}
			.isInstanceOf(InvalidCollectException::class.java)
			.hasMessage("Your ISO factory at Tau Ceti makes 6 units.")
	}

	@Test
	fun shouldRefuseAFactoryThePlayerDoesNotHave() {
		assertThatThrownBy { player.collect(listOf(OutputPlacement(GAMMA_LEPORIS, Goods.POLY, "Wayfarer", 1))) }
			.isInstanceOf(InvalidCollectException::class.java)
			.hasMessage("You have no POLY factory at Gamma Leporis.")
	}

	@Test
	fun shouldRefuseAShipThatIsNotInPortAtTheFactory() {
		// Lantern is at Mu Herculis, so it can't take Tau Ceti's output
		assertThatThrownBy { player.collect(listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Lantern", 1))) }
			.isInstanceOf(InvalidCollectException::class.java)
			.hasMessage("Choose your ships in port at Tau Ceti.")
	}

	@Test
	fun shouldRefuseAShipThatIsAtTheSystemButNotInPort() {
		val inSpace = player.copy(ships = listOf(wayfarer.copy(location = ShipLocation.SPACE), lantern))

		assertThatThrownBy { inSpace.collect(listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 1))) }
			.isInstanceOf(InvalidCollectException::class.java)
	}

	@Test
	fun shouldRefuseAWarehouseThePlayerDoesNotHaveAtThatSystem() {
		assertThatThrownBy { player.collect(listOf(OutputPlacement(MU_HERCULIS, Goods.CHIPS, null, 1))) }
			.isInstanceOf(InvalidCollectException::class.java)
			.hasMessage("You have no warehouse at Mu Herculis.")
	}

	@Test
	fun shouldRefuseANegativeAmount() {
		assertThatThrownBy { player.collect(listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", -1))) }
			.isInstanceOf(InvalidCollectException::class.java)
			.hasMessage("Enter a whole number of units, 0 or more, for each destination.")
	}

	@Test
	fun shouldChangeNothingWhenOnePlacementIsRefused() {
		// the player is immutable, so a refused collect can't half-apply
		assertThatThrownBy {
			player.collect(listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 2), OutputPlacement(TAU_CETI, Goods.ISO, "Lantern", 1)))
		}.isInstanceOf(InvalidCollectException::class.java)
		assertThat(player.ship("Wayfarer")!!.holding(Goods.ISO)).isZero()
	}

	@Test
	fun shouldHaveFactoriesOnlyOnceTheyOwnOne() {
		assertThat(player.hasFactories()).isTrue()
		assertThat(player.copy(factories = emptyList()).hasFactories()).isFalse()
	}
}
