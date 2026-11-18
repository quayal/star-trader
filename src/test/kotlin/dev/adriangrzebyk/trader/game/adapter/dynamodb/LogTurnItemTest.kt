package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.LogEntry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LogTurnItemTest {

	private val entries = listOf(
		LogEntry.event(4, "Initiative order (bid + d2): Vega Lines → Orion Freight"),
		LogEntry.event(4, "Vega Lines chose 1st")
	)

	@Test
	fun shouldReadBackTheTurnAndItsEntriesInOrder() {
		val item = LogTurnItem(LogTurnItem("game-1", 4, entries).attributes)

		assertThat(item.turn).isEqualTo(4)
		assertThat(item.entries()).isEqualTo(entries)
	}

	@Test
	fun shouldBeKeyedByGameAndTurnWithAnIdNoGameCanHave() {
		val stored = LogTurnItem("game-1", 4, entries).attributes

		assertThat(stored.keys).containsExactlyInAnyOrder("id", "turn", "entries")
		assertThat(stored.getValue("id").s()).isEqualTo("log#game-1#4")
		assertThat(logTurnItemId("game-1", 12)).isEqualTo("log#game-1#12")
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredItemIsIncomplete() {
		val broken = LogTurnItem("game-1", 4, entries).attributes - "entries"

		assertThatThrownBy { LogTurnItem(broken).entries() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("entries")
	}
}
