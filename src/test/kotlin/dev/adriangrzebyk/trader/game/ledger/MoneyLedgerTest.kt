package dev.adriangrzebyk.trader.game.ledger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R29 (playtest, 2026-11-20): a player's own money history, newest first.
class MoneyLedgerTest {

	@Test
	fun shouldRecordEachMovementInTheTurnItHappenedNewestFirst() {
		val ledger = MoneyLedger.START
			.recorded(-20, "Initiative bid")
			.inTurn(2)
			.recorded(72, "Sold 6 ISO at Tau Ceti")

		assertThat(ledger.newestFirst()).containsExactly(
			MoneyMovement(2, "Sold 6 ISO at Tau Ceti", 72),
			MoneyMovement(1, "Initiative bid", -20)
		)
	}

	@Test
	fun shouldIgnoreAMovementOfNothing() {
		assertThat(MoneyLedger.START.recorded(0, "Initiative bid").newestFirst()).isEmpty()
	}

	@Test
	fun shouldKeepOnlyTheNewestMovements() {
		val ledger = (1..MoneyLedger.KEPT + 5).fold(MoneyLedger.START) { kept, n -> kept.recorded(n, "Movement $n") }

		assertThat(ledger.newestFirst()).hasSize(MoneyLedger.KEPT)
		assertThat(ledger.newestFirst().first().amount).isEqualTo(MoneyLedger.KEPT + 5)
		assertThat(ledger.newestFirst().last().amount).isEqualTo(6)
	}

	@Test
	fun shouldTellCreditsFromDebits() {
		assertThat(MoneyMovement(1, "Loan taken", 100).isCredit()).isTrue()
		assertThat(MoneyMovement(1, "Loan interest", -3).isCredit()).isFalse()
	}
}
