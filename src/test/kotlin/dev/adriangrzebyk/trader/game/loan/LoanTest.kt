package dev.adriangrzebyk.trader.game.loan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R90–R95: a loan's interest, when it's due and when the loan falls due.
class LoanTest {

	@Test
	fun shouldFixTheInterestFromTheAmountAndReputationRoundedDown() {
		// R91's example: 300 HT at reputation 20 costs 14 HT a turn over 4 turns, 28 over 8
		assertThat(Loan.take(300, LoanTerm.FOUR, 20, 1).interest).isEqualTo(14)
		assertThat(Loan.take(300, LoanTerm.EIGHT, 20, 1).interest).isEqualTo(28)
	}

	@Test
	fun shouldChargeAtLeastOneHtAWhenReputationIsAtOrAboveTheAmount() {
		// Creator, 2026-11-20: interest is at least 1 HT
		assertThat(Loan.take(15, LoanTerm.FOUR, 20, 1).interest).isEqualTo(1)
		assertThat(Loan.take(20, LoanTerm.EIGHT, 20, 1).interest).isEqualTo(1)
		assertThat(Loan.take(39, LoanTerm.FOUR, 20, 1).interest).isEqualTo(1)
	}

	@Test
	fun shouldHaveInterestDueEveryTurnOfTheTermFromTheNextTurn() {
		// R92: taken in turn 3 for 4 turns, interest is due in turns 4 to 7, and it falls due when turn 7's Investment closes
		val loan = Loan.take(100, LoanTerm.FOUR, 20, 3)

		assertThat((1..9).filter { loan.isInterestDue(it) }).containsExactly(4, 5, 6, 7)
		assertThat((1..9).filter { loan.fallsDueIn(it) && it <= 7 }).containsExactly(7)
		assertThat(loan.turnsLeft(3)).isEqualTo(4)
		assertThat(loan.turnsLeft(5)).isEqualTo(3)
		assertThat(loan.turnsLeft(7)).isEqualTo(1)
	}

	@Test
	fun shouldAddUnpaidInterestToTheAmountAndKeepTheInterest() {
		// R92: "no change on interest payment"
		val loan = Loan.take(300, LoanTerm.FOUR, 20, 1).withInterestAdded()

		assertThat(loan.amount).isEqualTo(314)
		assertThat(loan.interest).isEqualTo(14)
	}

	@Test
	fun shouldRepayTheAmountPlusThisTurnsUnpaidInterest() {
		// R93
		val loan = Loan.take(300, LoanTerm.FOUR, 20, 1)

		assertThat(loan.repayment(interestUnpaid = true)).isEqualTo(314)
		assertThat(loan.repayment(interestUnpaid = false)).isEqualTo(300)
	}
}
