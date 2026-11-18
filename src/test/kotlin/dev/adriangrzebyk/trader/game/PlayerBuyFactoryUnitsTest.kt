package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R46, E06-S05: building or extending a factory at any system that can produce the good, with no ship needed.
class PlayerBuyFactoryUnitsTest {

	private val wayfarer = Ship.emptyAt("Wayfarer", TAU_CETI, ShipLocation.PORT)
	private val lantern = Ship.emptyAt("Lantern", MU_HERCULIS, ShipLocation.SPACE)
	private val drifter = Ship.emptyAt("Drifter", BETA_HYDRI, ShipLocation.PORT)

	private val prices = MarketPrices.board()
		.withPrice(MarketGood(TAU_CETI, Goods.ISO), 12)
		.withPrice(MarketGood(MU_HERCULIS, Goods.CHIPS), 12)

	// Political 10 + business 10: a factory cap of 20 units (R84), out of the way unless a test is about it
	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
		.startWith(300, listOf(wayfarer, lantern, drifter), 20)
		.placeConnections(10, 10, 0)

	@Test
	fun shouldCreateTheFactoryAtTheSizeBoughtAndTakeTheCost() {
		val bought = player.buyFactoryUnits(TAU_CETI, Goods.ISO, 3, prices)

		assertThat(bought.factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 3))
		assertThat(bought.money).isEqualTo(300 - 3 * 70)
	}

	@Test
	fun shouldGrowAnExistingFactoryByTheUnitsBought() {
		val owner = player.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 6)))

		val bought = owner.buyFactoryUnits(TAU_CETI, Goods.ISO, 2, prices)

		assertThat(bought.factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 8))
	}

	@Test
	fun shouldKeepOneFactoryPerGoodPerSystemAndLeaveTheOthersAlone() {
		val owner = player.copy(factories = listOf(Factory(MU_HERCULIS, Goods.CHIPS, 4)))

		val bought = owner.buyFactoryUnits(TAU_CETI, Goods.ISO, 1, prices).buyFactoryUnits(TAU_CETI, Goods.ISO, 1, prices)

		assertThat(bought.factories).containsExactlyInAnyOrder(Factory(MU_HERCULIS, Goods.CHIPS, 4), Factory(TAU_CETI, Goods.ISO, 2))
	}

	@Test
	fun shouldNeedNeitherAShipNorAFactoryThereAlready() {
		// creator, 2026-11-16: Gamma Leporis makes POLY and the player has no ship there and no factory
		val bought = player.buyFactoryUnits(GAMMA_LEPORIS, Goods.POLY, 2, prices)

		assertThat(bought.factories).containsExactly(Factory(GAMMA_LEPORIS, Goods.POLY, 2))
		assertThat(bought.money).isEqualTo(300 - 2 * ProductionCost(prices.priceOf(MarketGood(GAMMA_LEPORIS, Goods.POLY))).perUnit())
	}

	@Test
	fun shouldBuildForAPlayerWithNoShipsAtAll() {
		val shipless = player.copy(ships = emptyList())

		assertThat(shipless.buyFactoryUnits(TAU_CETI, Goods.ISO, 1, prices).factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 1))
	}

	@Test
	fun shouldRefuseAGoodTheSystemCannotProduce() {
		assertThatThrownBy { player.buyFactoryUnits(TAU_CETI, Goods.CHIPS, 1, prices) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Tau Ceti can't produce CHIPS.")
	}

	@Test
	fun shouldRefuseASystemWithNoFactoriesAtAll() {
		// R46: Beta Hydri and Epsilon Eridani produce nothing, even with a ship there
		for (goods in Goods.entries) {
			assertThatThrownBy { player.buyFactoryUnits(BETA_HYDRI, goods, 1, prices) }
				.isInstanceOf(InvalidInvestmentException::class.java)
				.hasMessage("Beta Hydri can't produce ${goods.name}.")
		}
	}

	@Test
	fun shouldRefuseMoreUnitsThanTheyCanPayFor() {
		assertThatThrownBy { player.buyFactoryUnits(TAU_CETI, Goods.ISO, 5, prices) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("5 units cost 350 HT; you have 300 HT.")
	}

	@Test
	fun shouldAllowSpendingEverythingUpToTheCap() {
		val rich = player.copy(money = 1400)

		val bought = rich.buyFactoryUnits(TAU_CETI, Goods.ISO, 20, prices)

		assertThat(bought.money).isZero()
		assertThat(bought.factories.single().size).isEqualTo(20)
	}

	@Test
	fun shouldRefuseUnitsBeyondPoliticalPlusBusinessAcrossEveryFactory() {
		// R84, E12-S03-7: 3 + 4 = 7 units in total; 4 at Mu Herculis and 3 at Tau Ceti leave no room for an 8th
		val owner = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
			.startWith(3000, emptyList(), 7)
			.placeConnections(3, 4, 0)
			.buyFactoryUnits(MU_HERCULIS, Goods.CHIPS, 4, prices)
			.buyFactoryUnits(TAU_CETI, Goods.ISO, 3, prices)

		assertThat(owner.factoryUnits()).isEqualTo(7)
		assertThatThrownBy { owner.buyFactoryUnits(GAMMA_LEPORIS, Goods.POLY, 1, prices) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("You can only own 7 units of factory (POL: 3, BUSS: 4).")
	}

	@Test
	fun shouldRefuseAnyFactoryToAPlayerWhoPutEverythingIntoCriminal() {
		val criminal = Player.newcomer("ada@example.com", "Ada", "Orion Freight").startWith(300, emptyList(), 5).placeConnections(0, 0, 5)

		assertThatThrownBy { criminal.buyFactoryUnits(TAU_CETI, Goods.ISO, 1, prices) }
			.hasMessage("You can only own 0 units of factory (POL: 0, BUSS: 0).")
	}

	@Test
	fun shouldKeepFactoriesAboveTheCapButSellNoMoreUntilBackUnderIt() {
		// E12-S03-9: nothing removes factories; a player over the cap just can't add to them
		val over = player.copy(connections = Connections(1, 1, 0), factories = listOf(Factory(TAU_CETI, Goods.ISO, 5)))

		assertThat(over.factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 5))
		assertThatThrownBy { over.buyFactoryUnits(TAU_CETI, Goods.ISO, 1, prices) }
			.hasMessage("You can only own 2 units of factory (POL: 1, BUSS: 1).")
	}

	@Test
	fun shouldRefuseAnAmountThatOverflowsTheCost() {
		assertThatThrownBy { player.buyFactoryUnits(TAU_CETI, Goods.ISO, Int.MAX_VALUE, prices) }
			.isInstanceOf(InvalidInvestmentException::class.java)
	}

	@Test
	fun shouldRefuseFewerThanOneUnit() {
		for (units in listOf(0, -1)) {
			assertThatThrownBy { player.buyFactoryUnits(TAU_CETI, Goods.ISO, units, prices) }
				.isInstanceOf(InvalidInvestmentException::class.java)
				.hasMessage("Enter a whole number of units, 1 or more.")
		}
	}

	@Test
	fun shouldSayUnitInTheSingular() {
		val poor = player.copy(money = 10)

		assertThatThrownBy { poor.buyFactoryUnits(TAU_CETI, Goods.ISO, 1, prices) }
			.hasMessage("1 unit costs 70 HT; you have 10 HT.")
	}
}
