package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R68: a 2d6 roll of the target or less makes the jump.
class JumpChanceTest {

	@Test
	fun shouldSucceedOnARollOfTheTargetOrLess() {
		assertThat(JumpChance(7).succeeds(7)).isTrue()
		assertThat(JumpChance(7).succeeds(2)).isTrue()
		assertThat(JumpChance(7).succeeds(8)).isFalse()
	}

	@Test
	fun shouldGiveTheChanceOfSuccessInWholePercent() {
		assertThat(JumpChance(2).percent()).isEqualTo(3) // 1 in 36
		assertThat(JumpChance(7).percent()).isEqualTo(58) // 21 in 36
		assertThat(JumpChance(9).percent()).isEqualTo(83) // 30 in 36
		assertThat(JumpChance(11).percent()).isEqualTo(97) // 35 in 36
	}

	@Test
	fun shouldBeCertainFromTwelveUp() {
		assertThat(JumpChance(12).percent()).isEqualTo(100)
		assertThat(JumpChance(15).percent()).isEqualTo(100)
		assertThat(JumpChance(15).succeeds(12)).isTrue()
	}
}
