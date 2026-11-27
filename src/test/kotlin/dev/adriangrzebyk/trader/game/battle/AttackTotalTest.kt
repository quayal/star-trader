package dev.adriangrzebyk.trader.game.battle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// R75: the creator's battle table, with the 21–25 and 26–30 rows corrected.
class AttackTotalTest {

	@ParameterizedTest
	@CsvSource(
		"2, 0, 0, 0, 0",
		"4, 0, 0, 0, 0",
		"5, 1, 0, 0, 0",
		"6, 1, 0, 0, 0",
		"7, 1, 1, 0, 0",
		"9, 1, 1, 0, 0",
		"10, 2, 1, 1, 0",
		"12, 2, 1, 1, 0",
		"13, 2, 2, 1, 1",
		"16, 2, 2, 1, 1",
		"17, 3, 2, 2, 1",
		"20, 3, 2, 2, 1",
		"21, 4, 3, 2, 2",
		"25, 4, 3, 2, 2",
		"26, 5, 4, 3, 2",
		"30, 5, 4, 3, 2",
		"31, 6, 5, 4, 3",
		"44, 6, 5, 4, 3"
	)
	fun shouldGiveTheHitsOfTheBattleTable(total: Int, shields1: Int, shields2: Int, shields3: Int, shields4: Int) {
		val attack = AttackTotal(total)

		assertThat(listOf(1, 2, 3, 4).map { attack.hitsAgainst(it) }).containsExactly(shields1, shields2, shields3, shields4)
	}

	@Test
	fun shouldGiveTheWholeTableForTheGuide() {
		// Creator, 2026-11-26: the Guide shows the table the battle reads
		val table = AttackTotal.table()

		assertThat(table).hasSize(9)
		assertThat(table.first()).isEqualTo(2 to listOf(0, 0, 0, 0))
		assertThat(table.last()).isEqualTo(31 to listOf(6, 5, 4, 3))
	}
}
