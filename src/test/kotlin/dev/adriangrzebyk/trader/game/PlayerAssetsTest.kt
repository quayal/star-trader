package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The Assets tab's numbers (creator, 2026-11-18): every good by type and where it is, at its mean price across systems. */
class PlayerAssetsTest {

	// ISO trades at 4 HT at Tau Ceti and 9 HT at Mu Herculis (mean 6.5, so 7); POLY only at Tau Ceti, 6 HT; FOOD nowhere.
	private val prices = MarketPrices(
		mapOf(
			MarketGood(StarSystem.TAU_CETI, Goods.ISO) to 4,
			MarketGood(StarSystem.MU_HERCULIS, Goods.ISO) to 9,
			MarketGood(StarSystem.TAU_CETI, Goods.POLY) to 6
		)
	)
	private val wayfarer = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT).load(Goods.ISO, 3).load(Goods.FOOD, 2)
	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight").startWith(300, listOf(wayfarer), 0).copy(
		warehouses = listOf(Warehouse(StarSystem.MU_HERCULIS, 1, mapOf(Goods.POLY to 2, Goods.ISO to 5))),
		factories = listOf(Factory(StarSystem.TAU_CETI, Goods.ISO, 3, 0))
	)

	@Test
	fun shouldPriceAGoodAtItsMeanAcrossTheSystemsThatTradeItToTheNearestHt() {
		assertThat(prices.averagePrice(Goods.ISO)).isEqualTo(7)
		assertThat(prices.averagePrice(Goods.POLY)).isEqualTo(6)
		assertThat(prices.averagePrice(Goods.FOOD)).isEqualTo(0)
	}

	@Test
	fun shouldGroupGoodsByTypeWithALotPerShipAndWarehouse() {
		val isoInWayfarer = GoodsLot(Goods.ISO, 3, StarSystem.TAU_CETI, "Wayfarer")
		val isoInWarehouse = GoodsLot(Goods.ISO, 5, StarSystem.MU_HERCULIS, null)

		assertThat(player.goodsStocks(prices)).containsExactly(
			GoodsStock(Goods.FOOD, 0, listOf(GoodsLot(Goods.FOOD, 2, StarSystem.TAU_CETI, "Wayfarer"))),
			GoodsStock(Goods.ISO, 7, listOf(isoInWayfarer, isoInWarehouse)),
			GoodsStock(Goods.POLY, 6, listOf(GoodsLot(Goods.POLY, 2, StarSystem.MU_HERCULIS, null)))
		)
	}

	@Test
	fun shouldValueGoodsAtTheMeanPriceWhereverTheyAre() {
		// 8 ISO × 7 + 2 POLY × 6 + 2 FOOD (traded nowhere) × 0.
		assertThat(player.goodsStocks(prices)[1].value()).isEqualTo(56)
		assertThat(player.goodsValue(prices)).isEqualTo(68)
	}

	@Test
	fun shouldFollowThePriceWhenItMoves() {
		val moved = prices.withPrice(MarketGood(StarSystem.TAU_CETI, Goods.ISO), 11)

		assertThat(moved.averagePrice(Goods.ISO)).isEqualTo(10)
	}

	@Test
	fun shouldValueShipsAtWhatTheyWouldFetchSold() {
		assertThat(player.shipsSaleValue()).isEqualTo(wayfarer.saleValue())
	}

	@Test
	fun shouldValueAFactoryByOneTurnsOutputAtTheMeanPrice() {
		assertThat(Factory(StarSystem.TAU_CETI, Goods.ISO, 3, 0).outputValue(prices)).isEqualTo(21)
		assertThat(player.factoryOutputValue(prices)).isEqualTo(21)
	}
}
