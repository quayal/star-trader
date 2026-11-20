package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.loan.Loan
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import dev.adriangrzebyk.trader.game.loan.SeizedUnits
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R90–R96: a player's loan, its collection, final money and selling factory units.
class PlayerLoanTest {

	private val prices = MarketPrices.board()

	/** What a unit of the Tau Ceti ISO factory sells for (R96): 5 × the board price there. */
	private val isoUnit = 5 * prices.priceOf(MarketGood(TAU_CETI, Goods.ISO))

	/** 300 HT, two starting ships (sale value 26 HT each: half of the Clarinet's 40 + 4 Cargo, Light weapons, Safe jump). */
	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight").startWith(
		300,
		listOf(Ship.emptyAt("Wayfarer", TAU_CETI, ShipLocation.PORT), Ship.emptyAt("Lantern", MU_HERCULIS, ShipLocation.PORT)),
		0
	)

	@Test
	fun shouldCapTheLoanAtShipsWarehouseBlocksAndCash() {
		// R90: 100 × 2 ships + 20 × 3 blocks + 300 cash
		val withBlocks = player.copy(warehouses = listOf(Warehouse(TAU_CETI, 2, emptyMap()), Warehouse(MU_HERCULIS, 1, emptyMap())))

		assertThat(withBlocks.borrowingCap()).isEqualTo(560)
	}

	@Test
	fun shouldPayTheLoanInAtOnceAndFixItsInterest() {
		val borrowed = player.takeLoan(300, LoanTerm.FOUR, 2)

		assertThat(borrowed.money).isEqualTo(600)
		assertThat(borrowed.loan).isEqualTo(Loan(300, 14, 2, LoanTerm.FOUR))
	}

	@Test
	fun shouldRefuseALoanAboveTheCapAWhileOneIsOpenOrWithoutATerm() {
		assertThatThrownBy { player.takeLoan(501, LoanTerm.FOUR, 1) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("You can borrow at most 500 HT.")
		assertThatThrownBy { player.takeLoan(100, LoanTerm.FOUR, 1).takeLoan(10, LoanTerm.EIGHT, 1) }
			.hasMessage("You already have a loan; repay it before taking another.")
		assertThatThrownBy { player.takeLoan(100, null, 1) }.hasMessage("Choose a term of 4 or 8 turns.")
		assertThatThrownBy { player.takeLoan(0, LoanTerm.FOUR, 1) }.hasMessage("Enter a whole number of HT, 1 or more.")
	}

	@Test
	fun shouldPayInterestOnlyWhileItsDueAndAffordable() {
		// R92
		val borrowed = player.takeLoan(300, LoanTerm.FOUR, 1)

		assertThatThrownBy { borrowed.payInterest(1) }.hasMessage("No interest is due this turn.")
		assertThat(borrowed.payInterest(2).money).isEqualTo(586)
		assertThatThrownBy { borrowed.copy(money = 9).payInterest(2) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Interest is 14 HT; you have 9 HT.")
	}

	@Test
	fun shouldRepayTheWholeLoanWithAnyUnpaidInterest() {
		// R93
		val borrowed = player.takeLoan(300, LoanTerm.FOUR, 1)

		val repaid = borrowed.repayLoan(interestUnpaid = true)

		assertThat(repaid.loan).isNull()
		assertThat(repaid.money).isEqualTo(600 - 314)
		assertThatThrownBy { borrowed.copy(money = 299).repayLoan(false) }.hasMessage("Repaying costs 300 HT; you have 299 HT.")
	}

	@Test
	fun shouldLoseFourReputationAndOweTheInterestWhenItGoesUnpaid() {
		// R92, R86
		val penalized = player.takeLoan(300, LoanTerm.FOUR, 1).withUnpaidInterest()

		assertThat(penalized.player.reputation.value).isEqualTo(16)
		assertThat(penalized.player.loan!!.amount).isEqualTo(314)
		assertThat(penalized.lines).containsExactly("Orion Freight's reputation: −4 for unpaid loan interest → 16")
	}

	@Test
	fun shouldCollectFromCashWhenItCoversTheLoanAndSeizeNothing() {
		// R94-1
		val (collected, collection) = player.takeLoan(200, LoanTerm.FOUR, 1).collectLoan(prices)

		assertThat(collected.money).isEqualTo(300)
		assertThat(collected.loan).isNull()
		assertThat(collection.seizedAnything()).isFalse()
		assertThat(collection.logText("Orion Freight")).isNull()
	}

	@Test
	fun shouldSeizeTheMostValuableShipsFirstThenFactoryUnitsAndWriteOffTheRest() {
		// R94: 40 cash; Lantern, a Leviathan, goes before the starting Wayfarer; then the ISO units at Tau Ceti before the
		// POLY ones at Gamma Leporis, which sell for less.
		val lantern = Ship.commission("Lantern", TAU_CETI, Hull.LEVIATHAN, emptyList(), CrewClass.D, 1).leaveYard()
		// Enough that two ISO units are needed after both ships, with a little surplus.
		val owed = 40 + lantern.saleValue() + player.ships[0].saleValue() + 2 * isoUnit - 5
		val owing = player.copy(
			money = 40,
			ships = listOf(player.ships[0], lantern),
			factories = listOf(Factory(GAMMA_LEPORIS, Goods.POLY, 2, 0), Factory(TAU_CETI, Goods.ISO, 3, 0)),
			loan = Loan(owed, 10, 1, LoanTerm.FOUR)
		)

		val (collected, collection) = owing.collectLoan(prices)

		// 40 cash + Lantern + Wayfarer leaves the rest to cover with ISO units.
		val left = owed - 40 - lantern.saleValue() - player.ships[0].saleValue()
		val units = (left + isoUnit - 1) / isoUnit
		assertThat(collection.ships).containsExactly("Lantern", "Wayfarer")
		assertThat(collection.factoryUnits).containsExactly(SeizedUnits(TAU_CETI, Goods.ISO, units))
		assertThat(collection.writtenOff).isZero()
		assertThat(collected.ships).isEmpty()
		assertThat(collected.money).isEqualTo(units * isoUnit - left)
		assertThat(collected.factoryAt(TAU_CETI, Goods.ISO)!!.size).isEqualTo(3 - units)
		assertThat(collection.logText("Orion Freight"))
			.isEqualTo("Orion Freight's loan fell due: Lantern, Wayfarer and $units u of the ISO factory at Tau Ceti seized")
	}

	@Test
	fun shouldWriteOffWhatNothingIsLeftToCover() {
		val broke = player.copy(money = 0, ships = emptyList(), loan = Loan(500, 25, 1, LoanTerm.FOUR))

		val (collected, collection) = broke.collectLoan(prices)

		assertThat(collection.writtenOff).isEqualTo(500)
		assertThat(collected.money).isZero()
		assertThat(collected.loan).isNull()
	}

	@Test
	fun shouldSubtractAnOpenLoanFromFinalMoney() {
		// R95: it can be negative
		assertThat(player.copy(loan = Loan(250, 12, 1, LoanTerm.FOUR)).finalMoney()).isEqualTo(50)
		assertThat(player.copy(money = 10, loan = Loan(250, 12, 1, LoanTerm.FOUR)).finalMoney()).isEqualTo(-240)
		assertThat(player.finalMoney()).isEqualTo(300)
	}

	@Test
	fun shouldSellFactoryUnitsForFiveTimesTheLocalPriceAndRemoveAnEmptiedFactory() {
		// R96: a unit sells for 5 × Tau Ceti's ISO price
		val owner = player.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 3, 1)))

		val sold = owner.sellFactoryUnits(TAU_CETI, Goods.ISO, 2, prices)

		assertThat(sold.money).isEqualTo(300 + 2 * isoUnit)
		assertThat(sold.factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 1, 1))
		assertThat(sold.sellFactoryUnits(TAU_CETI, Goods.ISO, 1, prices).factories).isEmpty()
		assertThatThrownBy { owner.sellFactoryUnits(TAU_CETI, Goods.ISO, 4, prices) }.hasMessage("Enter a whole number of units from 1 to 3.")
		assertThatThrownBy { owner.sellFactoryUnits(MU_HERCULIS, Goods.CHIPS, 1, prices) }.hasMessage("You have no CHIPS factory at Mu Herculis.")
	}
}
