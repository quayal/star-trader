package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R141: selling warehouse blocks for half what they cost; the player chooses what's lost so the rest fits.
class PlayerSellWarehouseTest {

	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight").startWith(300, emptyList(), 0)

	/** 3 blocks, 30 spaces: 12 ISO, 8 POLY and a stored Cargo module (5) use 25; FOOD takes none. */
	private val stocked = Warehouse(TAU_CETI, 3, mapOf(Goods.ISO to 12, Goods.POLY to 8, Goods.FOOD to 7), listOf(Module.CARGO))
	private val elsewhere = Warehouse(MU_HERCULIS, 1, mapOf(Goods.POLY to 2))
	private val owner = player.copy(warehouses = listOf(stocked, elsewhere))

	@Test
	fun shouldSellOneBlockForTenHtKeepingEverythingThatStillFits() {
		// 2 blocks hold 20 spaces; 25 are used, so 5 must go: here the module
		val sold = owner.sellWarehouseBlocks(TAU_CETI, 1, emptyMap(), mapOf(Module.CARGO to 1))

		assertThat(sold.money).isEqualTo(310)
		assertThat(sold.warehouseAt(TAU_CETI)).isEqualTo(Warehouse(TAU_CETI, 2, mapOf(Goods.ISO to 12, Goods.POLY to 8, Goods.FOOD to 7)))
		assertThat(sold.warehouseAt(MU_HERCULIS)).isEqualTo(elsewhere)
		assertThat(sold.ledger.newestFirst().first().reason).isEqualTo("Sold 1 warehouse block at Tau Ceti")
	}

	@Test
	fun shouldLetThePlayerDropGoodsInsteadOfTheModule() {
		val sold = owner.sellWarehouseBlocks(TAU_CETI, 1, mapOf(Goods.ISO to 5), emptyMap())

		assertThat(sold.warehouseAt(TAU_CETI)).isEqualTo(
			Warehouse(TAU_CETI, 2, mapOf(Goods.ISO to 7, Goods.POLY to 8, Goods.FOOD to 7), listOf(Module.CARGO))
		)
	}

	@Test
	fun shouldRefuseWhenWhatIsLeftDoesNotFit() {
		assertThatThrownBy { owner.sellWarehouseBlocks(TAU_CETI, 1, mapOf(Goods.ISO to 4), emptyMap()) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("2 blocks hold 20 cargo spaces: drop goods or modules to free 1 more.")
		assertThat(stocked.spacesToFree(1)).isEqualTo(5)
		assertThat(stocked.spacesToFree(3)).isZero()
	}

	@Test
	fun shouldRefuseToDropMoreThanItHolds() {
		assertThatThrownBy { owner.sellWarehouseBlocks(TAU_CETI, 1, mapOf(Goods.ISO to 13), emptyMap()) }
			.hasMessage("Your warehouse at Tau Ceti holds 12 ISO: drop from 0 to 12.")
		assertThatThrownBy { owner.sellWarehouseBlocks(TAU_CETI, 1, emptyMap(), mapOf(Module.CARGO to 2)) }
			.hasMessage("Your warehouse at Tau Ceti holds 1 Cargo: drop from 0 to 1.")
	}

	@Test
	fun shouldRemoveTheWarehouseWithAllItHeldWhenEveryBlockIsSold() {
		val sold = owner.sellWarehouseBlocks(TAU_CETI, 3, emptyMap(), emptyMap())

		assertThat(sold.money).isEqualTo(330)
		assertThat(sold.warehouses).containsExactly(elsewhere)
	}

	@Test
	fun shouldRefuseABlockCountOutsideWhatItHasOrNoWarehouse() {
		assertThatThrownBy { owner.sellWarehouseBlocks(TAU_CETI, 4, emptyMap(), emptyMap()) }
			.hasMessage("Enter a whole number of blocks from 1 to 3.")
		assertThatThrownBy { owner.sellWarehouseBlocks(TAU_CETI, 0, emptyMap(), emptyMap()) }
			.hasMessage("Enter a whole number of blocks from 1 to 3.")
		assertThatThrownBy { player.sellWarehouseBlocks(TAU_CETI, 1, emptyMap(), emptyMap()) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("You have no warehouse at Tau Ceti.")
	}
}
