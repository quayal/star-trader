package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.InterceptionRoll
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.Notice
import dev.adriangrzebyk.trader.game.NoticeKind
import dev.adriangrzebyk.trader.game.TradeRecord
import dev.adriangrzebyk.trader.game.battle.BattleSummary
import dev.adriangrzebyk.trader.game.battle.BattleSummarySection
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class LogEntryItemTest {

	private val entry = LogEntry.event(4, "Initiative (bid + 2d6): Vega Lines 0 + 9 = 9 → Orion Freight 0 + 3 = 3")
	private val trade = LogEntry.trade(
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
	fun shouldReadBackANoticeWithAndWithoutItsOptionalParts() {
		val roll =
			InterceptionRoll(
				"ada@example.com",
				"Wayfarer",
				"Wayfarer (2 + d6 5 + 0 = 7)",
				"grace@example.com",
				"Meridian",
				"Meridian (2 + d6 2 + 0 = 4)",
				true
			)
		val tried = LogEntry.notice(
			2,
			"Interception: …",
			Notice(NoticeKind.INTERCEPTION, StarSystem.TAU_CETI, listOf("ada@example.com"), null, roll)
		)
		val round = LogEntry.notice(2, "Battle at Tau Ceti, round 3", Notice(NoticeKind.BATTLE_ROUND, StarSystem.TAU_CETI, emptyList(), 3, null))

		assertThat(LogEntryItem(LogEntryItem(tried).attribute).toLogEntry()).isEqualTo(tried)
		assertThat(LogEntryItem(LogEntryItem(round).attribute).toLogEntry()).isEqualTo(round)
		assertThat(LogEntryItem(round).attribute.m().getValue("notice").m().keys).containsExactlyInAnyOrder("kind", "system", "players", "round")
	}

	@Test
	fun shouldReadBackABattlesSummary() {
		// Playtest 2026-11-20
		val summary = LogEntry.battleSummary(
			4,
			"Summary of the battle at Tau Ceti",
			Notice(NoticeKind.BATTLE_SUMMARY, StarSystem.TAU_CETI, listOf("ada@example.com"), null, null),
			BattleSummary(listOf(BattleSummarySection("Round 1", listOf("Meridian fires at Starfarer: 3 hits", "Starfarer is destroyed"))))
		)

		assertThat(LogEntryItem(LogEntryItem(summary).attribute).toLogEntry()).isEqualTo(summary)
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
