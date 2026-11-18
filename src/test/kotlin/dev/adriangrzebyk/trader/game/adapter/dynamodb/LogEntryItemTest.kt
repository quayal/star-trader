package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.TradeRecord
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class LogEntryItemTest {

	private val entry = LogEntry.event(4, "Initiative (bid + 2d6): Vega Lines 0 + 9 = 9 → Orion Freight 0 + 3 = 3")
	private val trade = LogEntry(
		4,
		"Vega Lines could buy up to 5 u: bought 3 u at 12 HT each, paid 36 HT",
		TradeRecord("grace@example.com", StarSystem.TAU_CETI, Goods.FOOD, OfferType.BUY, 3, 12)
	)

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
	fun shouldReadBackATradesRecord() {
		assertThat(LogEntryItem(LogEntryItem(trade).attribute).toLogEntry()).isEqualTo(trade)
	}

	@Test
	fun shouldStoreATradesRecordUnderTheseAttributeNames() {
		val stored = LogEntryItem(trade).attribute.m().getValue("trade").m()

		assertThat(stored.mapValues { it.value.s() ?: it.value.n() }).isEqualTo(
			mapOf("playerEmail" to "grace@example.com", "system" to "TAU_CETI", "goods" to "FOOD", "type" to "BUY", "units" to "3", "price" to "12")
		)
	}

	@Test
	fun shouldReadAnOlderItemsPlainStringAsTurnOne() {
		val older = AttributeValue.fromS("Game started: 2 players, 20 turns")

		assertThat(LogEntryItem(older).toLogEntry()).isEqualTo(LogEntry.event(1, "Game started: 2 players, 20 turns"))
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredEntryIsIncomplete() {
		val broken = AttributeValue.fromM(LogEntryItem(entry).attribute.m() - "turn")

		assertThatThrownBy { LogEntryItem(broken).toLogEntry() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("turn")
	}
}
