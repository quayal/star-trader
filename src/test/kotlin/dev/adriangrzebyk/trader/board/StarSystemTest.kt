package dev.adriangrzebyk.trader.board

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

	// R68: the creator's table, where the two directions of a route can differ.
	@Test
	fun shouldGiveEachDirectionOfARouteItsOwnBaseJumpChance() {
		assertThat(StarSystem.GAMMA_LEPORIS.baseJumpChanceTo(StarSystem.BETA_HYDRI)).isEqualTo(7)
		assertThat(StarSystem.BETA_HYDRI.baseJumpChanceTo(StarSystem.GAMMA_LEPORIS)).isEqualTo(5)
		assertThat(StarSystem.MU_HERCULIS.baseJumpChanceTo(StarSystem.TAU_CETI)).isEqualTo(9)
		assertThat(StarSystem.TAU_CETI.baseJumpChanceTo(StarSystem.MU_HERCULIS)).isEqualTo(4)
		assertThat(StarSystem.TAU_CETI.baseJumpChanceTo(StarSystem.EPSILON_ERIDANI)).isEqualTo(9)
	}

	@Test
	fun shouldHaveABaseJumpChanceBetweenEveryTwoSystemsAndNoneToItself() {
		StarSystem.entries.forEach { from ->
			assertThat(from.baseJumpChanceTo(from)).isNull()
			StarSystem.entries.filter { it != from }.forEach { to -> assertThat(from.baseJumpChanceTo(to)).isBetween(2, 12) }
		}
	}
}
