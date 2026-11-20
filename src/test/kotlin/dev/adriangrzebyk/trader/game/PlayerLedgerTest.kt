package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.ledger.MoneyMovement
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R29 (playtest, 2026-11-20): every change to a player's money is in their own ledger, stamped with its turn.
class PlayerLedgerTest {

	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight").receive(300, "Starting money")

	@Test
	fun shouldRecordWhatWasPaidAndReceivedWithTheTurnItHappenedIn() {
		val later = player.pay(20, "Initiative bid").inTurn(3).receive(72, "Sold 6 ISO at Tau Ceti")

		assertThat(later.money).isEqualTo(352)
		assertThat(later.ledger.newestFirst()).containsExactly(
			MoneyMovement(3, "Sold 6 ISO at Tau Ceti", 72),
			MoneyMovement(1, "Initiative bid", -20),
			MoneyMovement(1, "Starting money", 300)
		)
	}

	@Test
	fun shouldRecordBuyingReputationAndTakingALoanWithTheirReasons() {
		val invested = player.buyReputation(4).takeLoan(100, LoanTerm.FOUR, 1)

		assertThat(invested.ledger.newestFirst().map { it.reason to it.amount }).startsWith("Loan taken" to 100, "Bought reputation" to -20)
	}
}
