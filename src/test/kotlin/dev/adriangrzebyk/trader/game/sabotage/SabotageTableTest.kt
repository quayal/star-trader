package dev.adriangrzebyk.trader.game.sabotage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R98: the creator's Sabotage table.
class SabotageTableTest {

	private val table = SabotageTable()

	@Test
	fun shouldReadEachRowsEffectAndReputationLoss() {
		val rows = listOf(-3, 2, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 30)
		assertThat(rows.map { table.effect(it) }).containsExactly(0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 5, 5, 7, 7, 10, 10)
		assertThat(rows.map { table.reputationLoss(it) }).containsExactly(0, 0, 0, 10, 10, 10, 10, 6, 6, 4, 4, 0, 0, 0, 0, null, null)
	}
}
