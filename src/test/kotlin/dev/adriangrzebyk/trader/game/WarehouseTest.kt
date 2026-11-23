package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WarehouseTest {

	private val warehouse = Warehouse(StarSystem.TAU_CETI, 1, emptyMap())

	@Test
	fun shouldHoldTenCargoSpacesForEachBlock() {
		assertThat(Warehouse(StarSystem.TAU_CETI, 1, emptyMap()).cargoSpaces()).isEqualTo(Warehouse.CARGO_SPACES_PER_BLOCK)
		assertThat(Warehouse(StarSystem.TAU_CETI, 3, emptyMap()).cargoSpaces()).isEqualTo(30)
	}

	@Test
	fun shouldHaveAtLeastOneBlock() {
		for (blocks in listOf(0, -1)) {
			assertThatThrownBy { Warehouse(StarSystem.TAU_CETI, blocks, emptyMap()) }
				.isInstanceOf(IllegalArgumentException::class.java)
				.hasMessage("A warehouse has at least one block.")
		}
	}

	@Test
	fun shouldCountOnlyPolyAndIsoAgainstItsSpaces() {
		// R42, R49
		val stocked = warehouse.load(Goods.POLY, 4).load(Goods.ISO, 3).load(Goods.CHIPS, 50).load(Goods.FOOD, 50)

		assertThat(stocked.cargoSpacesUsed()).isEqualTo(7)
		assertThat(stocked.holding(Goods.CHIPS)).isEqualTo(50)
		assertThat(stocked.holding(Goods.FOOD)).isEqualTo(50)
	}

	@Test
	fun shouldLoseWhatDoesNotFitOfPolyAndIsoButNeverOfChipsAndFood() {
		// R16, R42
		val full = warehouse.load(Goods.POLY, 8)

		assertThat(full.unitsThatFit(Goods.ISO, 5)).isEqualTo(2)
		assertThat(full.load(Goods.ISO, 5).holding(Goods.ISO)).isEqualTo(2)
		assertThat(full.load(Goods.ISO, 5).load(Goods.POLY, 1).holding(Goods.POLY)).isEqualTo(8)
		assertThat(full.unitsThatFit(Goods.CHIPS, 500)).isEqualTo(500)
	}

	@Test
	fun shouldGrowWithMoreBlocks() {
		assertThat(warehouse.load(Goods.POLY, 15).holding(Goods.POLY)).isEqualTo(10)
		assertThat(warehouse.copy(blocks = 2).load(Goods.POLY, 15).holding(Goods.POLY)).isEqualTo(15)
	}

	@Test
	fun shouldUnloadWhatItSoldAndForgetAnEmptiedGood() {
		val stocked = warehouse.load(Goods.POLY, 6)

		assertThat(stocked.unload(Goods.POLY, 2).holding(Goods.POLY)).isEqualTo(4)
		assertThat(stocked.unload(Goods.POLY, 6).cargo).isEmpty()
	}

	@Test
	fun shouldRefuseToUnloadMoreThanItHolds() {
		assertThatThrownBy { warehouse.load(Goods.POLY, 3).unload(Goods.POLY, 4) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Your warehouse at Tau Ceti has only 3 POLY.")
	}

	@Test
	fun shouldCountOneSpacePerUnitOfPolyAndIsoOnly() {
		// R42, the same as a ship
		val warehouse = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 4, Goods.ISO to 3, Goods.CHIPS to 40))

		assertThat(warehouse.cargoSpacesUsed()).isEqualTo(7)
		assertThat(warehouse.freeSpacesFor(Goods.POLY)).isEqualTo(3)
		assertThat(warehouse.freeSpacesFor(Goods.CHIPS)).isNull()
	}

	@Test
	fun shouldStorePolyAndIsoUpToItsFreeSpaceAndLoseTheRest() {
		val warehouse = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 8))

		assertThat(warehouse.load(Goods.ISO, 5).holding(Goods.ISO)).isEqualTo(2)
		assertThat(warehouse.load(Goods.POLY, 5).holding(Goods.POLY)).isEqualTo(10)
	}

	@Test
	fun shouldStoreAnyAmountOfChipsAndFood() {
		val full = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 10))

		assertThat(full.load(Goods.FOOD, 60).holding(Goods.FOOD)).isEqualTo(60)
	}

	@Test
	fun shouldKeepNoEntryForAGoodThatNothingOfWasStored() {
		// like a ship's cargo: a full warehouse that can't take any ISO, or an amount of 0, leaves no ISO=0 behind
		val full = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 10))

		assertThat(full.load(Goods.ISO, 5)).isEqualTo(full)
		assertThat(full.load(Goods.CHIPS, 0)).isEqualTo(full)
	}

	@Test
	fun shouldCostTwentyHtABlockAndGrowByTheBlocksAdded() {
		// R45
		val stocked = Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.ISO to 3))

		assertThat(Warehouse.BLOCK_COST).isEqualTo(20)
		assertThat(stocked.addBlocks(2)).isEqualTo(Warehouse(StarSystem.TAU_CETI, 3, mapOf(Goods.ISO to 3)))
		assertThat(stocked.addBlocks(2).cargoSpaces()).isEqualTo(30)
	}

	@Test
	fun shouldStoreALegalModuleInFiveCargoSpaces() {
		// R122
		val stored = warehouse.load(Goods.POLY, 5).withModuleStored(Module.CARGO)

		assertThat(stored.modules).containsExactly(Module.CARGO)
		assertThat(stored.cargoSpacesUsed()).isEqualTo(10)
		assertThat(stored.unitsThatFit(Goods.ISO, 3)).isZero()
		assertThat(stored.hasRoomForModule()).isFalse()
		assertThatThrownBy { stored.withModuleStored(Module.PASSENGER) }
			.hasMessage("Your warehouse at Tau Ceti has room for no more modules: one takes 5 cargo spaces.")
	}

	@Test
	fun shouldRefuseToStoreAMilitaryModule() {
		// R122, R112: nothing illegal goes into a warehouse
		assertThatThrownBy { warehouse.withModuleStored(Module.DOGFIGHT) }
			.hasMessage("Dogfight is military: it can't be stored in a warehouse.")
	}

	@Test
	fun shouldGiveBackAStoredModuleAndRefuseOneItDoesNotHold() {
		// R122
		val stored = warehouse.withModuleStored(Module.CARGO).withModuleStored(Module.CARGO)

		assertThat(stored.withModuleTaken(Module.CARGO).modules).containsExactly(Module.CARGO)
		assertThatThrownBy { stored.withModuleTaken(Module.PASSENGER) }
			.hasMessage("Your warehouse at Tau Ceti holds no Passenger module.")
	}

	@Test
	fun shouldKeepStoredModulesWhenEmptied() {
		// E12's pirates take goods only (spec 2026-11-23)
		val looted = warehouse.load(Goods.POLY, 3).withModuleStored(Module.CARGO).emptied()

		assertThat(looted.cargo).isEmpty()
		assertThat(looted.modules).containsExactly(Module.CARGO)
	}
}
