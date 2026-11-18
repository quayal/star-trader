package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ReputationTest {

	@Test
	fun shouldStartAtTwentyAndClampEveryChangeToZeroToForty() {
		assertThat(Reputation.INITIAL.value).isEqualTo(20)
		assertThat(Reputation(3).changedBy(-12).value).isEqualTo(0)
		assertThat(Reputation(38).changedBy(6).value).isEqualTo(40)
		assertThat(Reputation(20).changedBy(-7).value).isEqualTo(13)
		assertThatThrownBy { Reputation(41) }.isInstanceOf(IllegalArgumentException::class.java)
	}

	@Test
	fun shouldRecoverOnlyFromOneToSeventeen() {
		// R86 (creator, 2026-11-18): a player at 0 has to buy their way back.
		assertThat(listOf(0, 1, 17, 18, 40).map { Reputation(it).recovery() }).containsExactly(0, 3, 3, 0, 0)
	}

	@Test
	fun shouldPayTheCreatorsBonusFromTwentyFive() {
		assertThat(listOf(24, 25, 27, 29, 30, 33, 34, 36, 39, 40).map { Reputation(it).bonus() })
			.containsExactly(0, 5, 10, 15, 20, 20, 25, 30, 35, 40)
	}
}
