package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.LogEntry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class LogEntryItemTest {

	private val entry = LogEntry(4, "Initiative order (bid + d2): Vega Lines → Orion Freight")

	@Test
	fun shouldReadBackTheTurnAndText() {
		assertThat(LogEntryItem(LogEntryItem(entry).attribute).toLogEntry()).isEqualTo(entry)
	}

	@Test
	fun shouldStoreAMapOfTurnAndText() {
		val stored = LogEntryItem(entry).attribute.m()

		assertThat(stored.keys).containsExactlyInAnyOrder("turn", "text")
		assertThat(stored.getValue("turn").n()).isEqualTo("4")
	}

	@Test
	fun shouldReadAnOlderItemsPlainStringAsTurnOne() {
		val older = AttributeValue.fromS("Game started: 2 players, 20 turns")

		assertThat(LogEntryItem(older).toLogEntry()).isEqualTo(LogEntry(1, "Game started: 2 players, 20 turns"))
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredEntryIsIncomplete() {
		val broken = AttributeValue.fromM(LogEntryItem(entry).attribute.m() - "turn")

		assertThatThrownBy { LogEntryItem(broken).toLogEntry() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("turn")
	}
}
