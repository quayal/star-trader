package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.ADA
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.GRACE
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import dev.adriangrzebyk.trader.game.loan.Loan
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R90–R96 (D22): loans in Investment, their interest and collection when Investment closes, and selling factory units.
class GameLoanTest {

	private val fixture = OpportunityGame()
	private val investing = fixture.opportunity.advancePhase(FakeDice())

	/** [game] taken from Investment to the next turn's Investment: everyone's interest goes unpaid unless paid first. */
	private fun nextInvestment(game: Game): Game = (1..5).fold(game) { current, _ -> current.advancePhase(FakeDice(2, 1, 3)) }

	@Test
	fun shouldBorrowPrivatelyInInvestmentOnly() {
		val borrowed = investing.takeLoan(ADA, 300, LoanTerm.FOUR)

		assertThat(fixture.playerOf(borrowed, ADA).money).isEqualTo(600)
		assertThat(borrowed.log).isEqualTo(investing.log)
		assertThat(borrowed.viewFor(ADA)!!.loan!!.loan).isEqualTo(Loan(300, 14, 1, LoanTerm.FOUR))
		assertThat(borrowed.viewFor(GRACE)!!.loan!!.loan).isNull()
		assertThatThrownBy { fixture.opportunity.takeLoan(ADA, 100, LoanTerm.FOUR) }.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldPenalizeUnpaidInterestWhenInvestmentClosesAndAddItToTheLoan() {
		// R92: due from turn 2; Ada pays nothing
		val turn2 = nextInvestment(investing.takeLoan(ADA, 300, LoanTerm.FOUR))

		val closed = turn2.advancePhase(FakeDice())

		assertThat(fixture.playerOf(closed, ADA).loan!!.amount).isEqualTo(314)
		assertThat(closed.log.map { it.text }).contains("Orion Freight's reputation: −4 for unpaid loan interest → 16")
	}

	@Test
	fun shouldTakeNoPenaltyOncePaidAndRefuseASecondPayment() {
		val turn2 = nextInvestment(investing.takeLoan(ADA, 300, LoanTerm.FOUR))
		val paid = turn2.payInterest(ADA)

		assertThatThrownBy { paid.payInterest(ADA) }.hasMessage("You've already paid this turn's interest.")
		val closed = paid.advancePhase(FakeDice())
		assertThat(fixture.playerOf(closed, ADA).loan!!.amount).isEqualTo(300)
		assertThat(closed.log.map { it.text }).noneMatch { it.contains("unpaid loan interest") }
	}

	@Test
	fun shouldCollectTheLoanWhenItsTermEnds() {
		// R94: a 4-turn loan taken in turn 1 is collected when turn 5's Investment closes; cash covers it, nothing is logged
		val turn5 = (2..5).fold(investing.takeLoan(ADA, 100, LoanTerm.FOUR)) { game, _ -> nextInvestment(game).payInterest(ADA) }

		val closed = turn5.advancePhase(FakeDice())

		assertThat(fixture.playerOf(closed, ADA).loan).isNull()
		assertThat(fixture.playerOf(closed, ADA).money).isEqualTo(300 - 4 * 4)
	}

	@Test
	fun shouldRankByCashLessAnOpenLoanAtTheEnd() {
		// R95: Ada's 300 HT less a 250 HT loan
		val owing = fixture.with(investing, ADA) { it.copy(loan = Loan(250, 12, 1, LoanTerm.EIGHT)) }

		assertThat(owing.standings().map { it.email }).containsExactly(GRACE, KIM, ADA)
		assertThat(owing.winners().map { it.email }).containsExactly(GRACE, KIM)
	}

	@Test
	fun shouldSellFactoryUnitsAndLogItWithoutTheAmount() {
		// R96
		val owner = fixture.with(investing, KIM) { it.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 3, 0))) }

		val sold = owner.sellFactoryUnits(KIM, TAU_CETI, Goods.ISO, 2)

		assertThat(fixture.playerOf(sold, KIM).factoryAt(TAU_CETI, Goods.ISO)!!.size).isEqualTo(1)
		assertThat(sold.log.last().text).isEqualTo("Kessler Cargo sold 2 u of its ISO factory at Tau Ceti")
	}
}
