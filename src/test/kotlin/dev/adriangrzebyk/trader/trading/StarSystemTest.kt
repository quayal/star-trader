package dev.adriangrzebyk.trader.trading

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StarSystemTest {

	@Test
	fun shouldGiveEachSystemItsLocationStatistics() {
		assertThat(StarSystem.TAU_CETI.locations).isEqualTo(LocationStats(portClass = 4, legalSystem = 2, police = 3, security = 4))
	}

	@Test
	fun shouldKeepEveryLocationStatisticOnTheOneToFiveScale() {
		val values = StarSystem.entries.flatMap {
			listOf(it.locations.portClass, it.locations.legalSystem, it.locations.police, it.locations.security)
		}

		assertThat(values).allMatch { it in 1..5 }
	}

	@Test
	fun shouldSayWhichGoodsEachSystemCanProduce() {
		assertThat(StarSystem.GAMMA_LEPORIS.producibleGoods).containsExactly(Goods.POLY)
		assertThat(StarSystem.SIGMA_DRACONIS.producibleGoods).containsExactlyInAnyOrder(Goods.FOOD, Goods.ISO)
		assertThat(StarSystem.MU_HERCULIS.producibleGoods).containsExactly(Goods.CHIPS)
		assertThat(StarSystem.TAU_CETI.producibleGoods).containsExactly(Goods.ISO)
	}

	@Test
	fun shouldLetNoFactoryStandAtBetaHydriOrEpsilonEridani() {
		assertThat(StarSystem.BETA_HYDRI.producibleGoods).isEmpty()
		assertThat(StarSystem.EPSILON_ERIDANI.producibleGoods).isEmpty()
	}

	@Test
	fun shouldOnlyProduceGoodsThatTheSystemAlsoTrades() {
		assertThat(StarSystem.entries).allMatch { it.goods.containsAll(it.producibleGoods) }
	}
}
