package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R46: N units cost N × (5 × the good's current price + 10) HT.
class ProductionCostTest {

	@Test
	fun shouldCostFiveTimesThePricePlusTenAUnit() {
		assertThat(ProductionCost(12).perUnit()).isEqualTo(70)
		assertThat(ProductionCost(1).perUnit()).isEqualTo(15)
		assertThat(ProductionCost(20).perUnit()).isEqualTo(110)
	}

	@Test
	fun shouldMultiplyByTheUnits() {
		assertThat(ProductionCost(12).of(3)).isEqualTo(210)
		assertThat(ProductionCost(12).of(0)).isZero()
	}

	@Test
	fun shouldNotOverflowForAnAbsurdAmount() {
		assertThat(ProductionCost(20).of(Int.MAX_VALUE)).isEqualTo(Int.MAX_VALUE.toLong() * 110)
	}
}
