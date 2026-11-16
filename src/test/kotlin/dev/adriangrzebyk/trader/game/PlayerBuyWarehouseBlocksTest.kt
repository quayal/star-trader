package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R45, E06-S04: buying warehouse blocks where the player has a ship, at 20 HT a block.
class PlayerBuyWarehouseBlocksTest {

	private val wayfarer = Ship.emptyAt("Wayfarer", TAU_CETI, ShipLocation.PORT)
	private val lantern = Ship.emptyAt("Lantern", MU_HERCULIS, ShipLocation.SPACE)

	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight").startWith(300, listOf(wayfarer, lantern))

	@Test
	fun shouldCreateTheWarehouseAndTakeTwentyHtABlock() {
		val bought = player.buyWarehouseBlocks(TAU_CETI, 3)

		assertThat(bought.warehouseAt(TAU_CETI)).isEqualTo(Warehouse(TAU_CETI, 3, emptyMap()))
		assertThat(bought.money).isEqualTo(300 - 3 * 20)
	}

	@Test
	fun shouldAddToAnExistingWarehouseAndKeepWhatItHolds() {
		val stocked = player.copy(warehouses = listOf(Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 4))))

		val bought = stocked.buyWarehouseBlocks(TAU_CETI, 2)

		assertThat(bought.warehouseAt(TAU_CETI)).isEqualTo(Warehouse(TAU_CETI, 3, mapOf(Goods.ISO to 4)))
		assertThat(bought.warehouseAt(TAU_CETI)!!.cargoSpaces()).isEqualTo(30)
	}

	@Test
	fun shouldLeaveOtherWarehousesAlone() {
		val elsewhere = Warehouse(MU_HERCULIS, 2, mapOf(Goods.POLY to 5))
		val bought = player.copy(warehouses = listOf(elsewhere)).buyWarehouseBlocks(TAU_CETI, 1)

		assertThat(bought.warehouseAt(MU_HERCULIS)).isEqualTo(elsewhere)
		assertThat(bought.warehouses).hasSize(2)
	}

	@Test
	fun shouldAllowAShipInAnyLocationAtTheSystem() {
		// the spec says "a ship at that system", not "in port": Lantern is in Space at Mu Herculis
		assertThat(player.buyWarehouseBlocks(MU_HERCULIS, 1).warehouseAt(MU_HERCULIS)).isNotNull()
	}

	@Test
	fun shouldRefuseAPlayerWithNoShipAtTheSystem() {
		assertThatThrownBy { player.buyWarehouseBlocks(GAMMA_LEPORIS, 1) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("You need a ship at Gamma Leporis to build there.")
	}

	@Test
	fun shouldRefuseMoreBlocksThanTheyCanPayFor() {
		assertThatThrownBy { player.buyWarehouseBlocks(TAU_CETI, 16) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("16 blocks cost 320 HT; you have 300 HT.")
	}

	@Test
	fun shouldAllowSpendingEverything() {
		val bought = player.buyWarehouseBlocks(TAU_CETI, 15)

		assertThat(bought.money).isZero()
		assertThat(bought.warehouseAt(TAU_CETI)!!.blocks).isEqualTo(15)
	}

	@Test
	fun shouldRefuseAnAmountThatOverflowsTheCost() {
		assertThatThrownBy { player.buyWarehouseBlocks(TAU_CETI, Int.MAX_VALUE) }
			.isInstanceOf(InvalidInvestmentException::class.java)
	}

	@Test
	fun shouldRefuseFewerThanOneBlock() {
		for (blocks in listOf(0, -1)) {
			assertThatThrownBy { player.buyWarehouseBlocks(TAU_CETI, blocks) }
				.isInstanceOf(InvalidInvestmentException::class.java)
				.hasMessage("Enter a whole number of blocks, 1 or more.")
		}
	}

	@Test
	fun shouldSayBlockInTheSingular() {
		assertThatThrownBy { Player.newcomer("a@example.com", "A", "A Ltd").startWith(10, listOf(wayfarer)).buyWarehouseBlocks(TAU_CETI, 1) }
			.hasMessage("1 block costs 20 HT; you have 10 HT.")
	}
}
