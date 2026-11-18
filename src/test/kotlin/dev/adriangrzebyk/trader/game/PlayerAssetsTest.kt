package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The Assets tab's numbers (creator, 2026-11-18): where a player's money sits, at today's prices. */
class PlayerAssetsTest {

	// Tau Ceti trades POLY at 6 HT and ISO at 4 HT on the board, and no FOOD.
	private val prices = MarketPrices.board()
	private val wayfarer = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT).load(Goods.ISO, 3).load(Goods.FOOD, 2)
	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight").startWith(300, listOf(wayfarer), 0).copy(
		warehouses = listOf(Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.POLY to 2))),
		factories = listOf(Factory(StarSystem.TAU_CETI, Goods.ISO, 3))
	)

	@Test
	fun shouldValueEachHeldGoodAtTodaysPriceWhereItIs() {
		assertThat(wayfarer.heldGoods(prices)).containsExactly(HeldGoods(Goods.FOOD, 2, null), HeldGoods(Goods.ISO, 3, 4))
	}

	@Test
	fun shouldFollowThePriceWhenItMoves() {
		val moved = prices.withPrice(MarketGood(StarSystem.TAU_CETI, Goods.ISO), 9)

		assertThat(wayfarer.heldGoods(moved).last()).isEqualTo(HeldGoods(Goods.ISO, 3, 9))
	}

	@Test
	fun shouldCountGoodsInShipsAndWarehousesAndNothingForAGoodNotTradedThere() {
		// 3 ISO × 4 + 2 FOOD (not traded at Tau Ceti) + 2 POLY × 6.
		assertThat(player.goodsValue(prices)).isEqualTo(24)
	}

	@Test
	fun shouldValueShipsAtWhatTheyWouldFetchSold() {
		assertThat(player.shipsSaleValue()).isEqualTo(wayfarer.saleValue())
	}

	@Test
	fun shouldValueAFactoryByOneTurnsOutputAtTodaysPrice() {
		assertThat(Factory(StarSystem.TAU_CETI, Goods.ISO, 3).outputValue(prices)).isEqualTo(12)
		assertThat(player.factoryOutputValue(prices)).isEqualTo(12)
	}
}
