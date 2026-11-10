package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E05-S02: offers are disclosed and the Dictator may act (R38). E05-S03: the counter resolves the offers (R34).
// E05-S04: it waits for each trader to choose an amount.
class ResolutionTest {

	private val initiative = listOf("vega@example.com", "orion@example.com", "kessler@example.com")

	private val good = MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)
	private val vega = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)
	private val orion = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 10)
	private val otherGood = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.CHIPS, OfferType.SELL, 4)
	private val otherSystem = Offer("orion@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 6)

	/** [at] starting to resolve at its board price with [offers] and [dictator], and no Wholesalers. */
	private fun start(offers: List<Offer>, dictator: String?, at: MarketGood = good): Resolution =
		Resolution.start(at, at.boardPrice(), offers, dictator, emptyList(), initiative)

	@Test
	fun shouldDiscloseOnlyTheOffersForThatGoodAtThatSystemInTheOrderTheyWerePlaced() {
		val resolution = start(listOf(vega, otherGood, otherSystem, orion), null)

		assertThat(resolution.offers).containsExactly(vega, orion)
	}

	@Test
	fun shouldAskNobodyBeforeTheRollWithNoDictator() {
		// S02-4
		val resolution = start(listOf(vega), null)

		assertThat(resolution.awaitingDictator()).isNull()
		assertThat(resolution.awaitingTrade()).isNull()
		assertThat(resolution.dictatorOffer()).isNull()
	}

	@Test
	fun shouldWaitForTheDictatorUntilTheyDecide() {
		// S02-2
		val resolution = start(listOf(vega), "altair@example.com")
		val dictators = Offer("altair@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 10)

		assertThat(resolution.awaitingDictator()).isEqualTo("altair@example.com")
		assertThat(resolution.isFinished()).isFalse()
		assertThat(resolution.withDictatorOffer(dictators).offers).containsExactly(vega, dictators)
		assertThat(resolution.withDictatorOffer(dictators).dictatorOffer()).isEqualTo(dictators)
		assertThat(resolution.withDictatorOffer(dictators).awaitingDictator()).isNull()
		assertThat(resolution.withDictatorPassed().offers).containsExactly(vega)
		assertThat(resolution.withDictatorPassed().awaitingDictator()).isNull()
	}

	@Test
	fun shouldAskTheDictatorOnce() {
		val passed = start(emptyList(), "altair@example.com").withDictatorPassed()

		assertThatThrownBy { passed.withDictatorPassed() }.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("No Dictator decision is pending.")
		assertThatThrownBy { start(emptyList(), null).withDictatorOffer(vega) }
			.hasMessage("No Dictator decision is pending.")
	}

	@Test
	fun shouldDescribeAnOfferForThePublicLog() {
		assertThat(vega.terms()).isEqualTo("buy at 11 HT")
		assertThat(orion.terms()).isEqualTo("sell at 10 HT")
	}

	// Mu Herculis ISO starts at 11 HT with S/D +1; Tau Ceti ISO at 4 HT with S/D -9.
	private val kessler = Offer("kessler@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 10)
	private val tauCetiIso = MarketGood(StarSystem.TAU_CETI, Goods.ISO)

	/** [resolution] after each awaited trader in turn traded their max: how the counter ran before E05-S04. */
	private tailrec fun tradingTheirMax(resolution: Resolution): Resolution {
		val awaited = resolution.awaitingTrade() ?: return resolution
		return tradingTheirMax(resolution.withTrade(awaited.marketMax, null))
	}

	@Test
	fun shouldWaitForEachTraderInTheWorkedExample() {
		// The spec's worked example: roll 2, counter 3. Vega wins and may buy up to 3 - (-1) = 4; she buys 4 (counter
		// -1). Orion may then sell up to 4 - (-1) = 5; he sells 5 (counter 4); nobody is left.
		val started = start(listOf(vega, orion), null).startCounterOnceDecided(FakeDice.twoD6(2))

		assertThat(started.startingCounter).isEqualTo(3)
		assertThat(started.awaitingTrade()).isEqualTo(AwaitedTrade(vega, 4))
		assertThat(started.isFinished()).isFalse()
		val vegaBought = started.withTrade(4, null)
		assertThat(vegaBought.awaitingTrade()).isEqualTo(AwaitedTrade(orion, 5))
		val orionSold = vegaBought.withTrade(5, null)
		assertThat(orionSold.awaitingTrade()).isNull()
		assertThat(orionSold.isFinished()).isTrue()
		assertThat(orionSold.counter()).isEqualTo(4)
		assertThat(orionSold.marketText()).isEqualTo("market +3 → buyers first")
		assertThat(orionSold.endText()).isEqualTo("No one else can trade")
	}

	@Test
	fun shouldLeaveMoreForOthersWhenATraderTakesLess() {
		// The worked example's variant: Vega buys only 1 (counter 2), so Orion may sell only 4 - 2 = 2.
		val started = start(listOf(vega, orion), null).startCounterOnceDecided(FakeDice.twoD6(2))

		assertThat(started.withTrade(1, null).awaitingTrade()).isEqualTo(AwaitedTrade(orion, 2))
	}

	@Test
	fun shouldLetAThirdOfferTradeWhatTheCounterStillAllows() {
		// The worked example's variant: Kessler buying at 10 (target 2) may buy 4 - 2 = 2 after Orion's sell.
		val resolution =
			tradingTheirMax(start(listOf(kessler, orion, vega), null).startCounterOnceDecided(FakeDice.twoD6(2)))

		assertThat(resolution.trades).containsExactly(Trade(vega, 4, 4, null), Trade(orion, 5, 5, null), Trade(kessler, 2, 2, null))
		assertThat(resolution.counter()).isEqualTo(2)
	}

	@Test
	fun shouldLetTheLowestSellTradeFirstWhenTheCounterIsNegative() {
		// S03-2: counter -9 + 5 = -4, so the sell at 4 (target 1) trades first, 5 u, though the buy at 11 (saturated,
		// target -18) allows 14. Then the most units at each counter: the buy 19 (to -18), then the sell at 5 (target -2) 16.
		val sellAt4 = Offer("orion@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 4)
		val sellAt5 = Offer("kessler@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 5)
		val buyAt11 = Offer("vega@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 11)

		val resolution = tradingTheirMax(
			start(listOf(buyAt11, sellAt5, sellAt4), null, tauCetiIso).startCounterOnceDecided(FakeDice.twoD6(5))
		)

		assertThat(resolution.trades).containsExactly(Trade(sellAt4, 5, 5, null), Trade(buyAt11, 19, 19, null), Trade(sellAt5, 16, 16, null))
		assertThat(resolution.counter()).isEqualTo(-2)
		assertThat(resolution.marketText()).isEqualTo("market \u22124 → sellers first")
	}

	@Test
	fun shouldLetTheOtherSideTradeFirstWhenTheWinningSideHasNoOfferThatCan() {
		// Counter 3 favours buyers, but there are none: Orion's sell (target 4) may still sell 1.
		val resolution = start(listOf(orion), null).startCounterOnceDecided(FakeDice.twoD6(2))

		assertThat(resolution.awaitingTrade()).isEqualTo(AwaitedTrade(orion, 1))
	}

	@Test
	fun shouldBreakABuyerSellerTieByTheCountersSignThenInitiative() {
		// S03-3, reachable now that a trader may take less than their max. At Mu Herculis ISO, Vega buys 1 of her 4
		// (counter 2): a buy at 12 (target -3) and a sell at 9 (target 7) both allow 5, and the counter is 0 or more, so
		// the buyer. At Tau Ceti ISO, Kessler sells 2 of 4 (counter -2): a buy at 6 (target -5) and a sell at 4 (target 1)
		// both allow 3: the seller.
		val buyAt12 = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 12)
		val sellAt9 = Offer("kessler@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 9)
		val buyAt13 = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 13)
		val atTwo =
			Resolution(
				good, 11,
				listOf(
					buyAt13,
					sellAt9,
					buyAt12
				),
				null, false, 3, listOf(Trade(buyAt13, 6, 1, null)), initiative, emptyList(), emptyList()
			)
		val buyAt6 = Offer("vega@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 6)
		val sellAt4 = Offer("orion@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 4)
		val sellAt3 = Offer("kessler@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 3)
		val atMinusTwo =
			Resolution(
				tauCetiIso, 4,
				listOf(
					sellAt3,
					buyAt6,
					sellAt4
				),
				null, false, -4, listOf(Trade(sellAt3, 4, 2, null)), initiative, emptyList(), emptyList()
			)

		assertThat(atTwo.awaitingTrade()).isEqualTo(AwaitedTrade(buyAt12, 5))
		assertThat(atMinusTwo.awaitingTrade()).isEqualTo(AwaitedTrade(sellAt4, 3))
	}

	@Test
	fun shouldMoveOnWithoutMovingTheCounterWhenATraderCouldTradeNothing() {
		// E05-S04-5: Vega can't afford a unit, so her offer is done with none; the counter stays at 3 for Orion (target 4: 1 u).
		val started = start(listOf(vega, orion), null).startCounterOnceDecided(FakeDice.twoD6(2))

		val skipped = started.withTrade(0, null)

		assertThat(skipped.counter()).isEqualTo(3)
		assertThat(skipped.awaitingTrade()).isEqualTo(AwaitedTrade(orion, 1))
		assertThat(skipped.withTrade(0, null).endText()).isEqualTo("No one can trade")
	}

	@Test
	fun shouldRefuseATradeThatIsNotTheAwaitedOne() {
		val started = start(listOf(vega, orion), null).startCounterOnceDecided(FakeDice.twoD6(2))

		assertThatThrownBy { started.withTrade(5, null) }.isInstanceOf(IllegalStateException::class.java)
		assertThatThrownBy { started.withTrade(-1, null) }.isInstanceOf(IllegalStateException::class.java)
		assertThatThrownBy { start(listOf(vega), null).withTrade(1, null) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("No trade is waiting for a decision.")
	}

	@Test
	fun shouldFinishAtOnceWithNoOfferThatCanTrade() {
		// S03-7: no offers, or one priced out of the market (a buy at 9, target 5, at counter 3), and nobody trades.
		val buyAt9 = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 9)

		listOf(emptyList(), listOf(buyAt9)).forEach { offers ->
			val resolution = start(offers, null).startCounterOnceDecided(FakeDice.twoD6(2))

			assertThat(resolution.startingCounter).isEqualTo(3)
			assertThat(resolution.trades).isEmpty()
			assertThat(resolution.isFinished()).isTrue()
			assertThat(resolution.endText()).isEqualTo("No one can trade")
		}
	}

	// E05-S05: the worked example's Mu Herculis ISO, with Altair Dictator and Kessler Wholesaler there, Kessler with no
	// offer for it. Initiative: Kessler, Vega, Altair, Orion.
	private val withPositions = listOf("kessler@example.com", "vega@example.com", "altair@example.com", "orion@example.com")

	private fun workedExample(dictator: String?, wholesalers: List<String>): Resolution =
		Resolution.start(good, 11, listOf(vega, orion), dictator, wholesalers, withPositions)

	@Test
	fun shouldChangeThePriceOnceByTheCountersFinalPositionInTheWorkedExample() {
		// S05-1, R35: Vega buys 4, Orion sells 5, counter 4: 11 + modifier(4) = 10 HT. No price until trading is over.
		val started = workedExample(null, emptyList()).startCounterOnceDecided(FakeDice.twoD6(2))

		assertThat(started.newPrice()).isNull()
		assertThat(started.priceText()).isNull()
		assertThat(started.withTrade(4, null).newPrice()).isNull()
		val over = started.withTrade(4, null).withTrade(5, null)
		assertThat(over.isTradingOver()).isTrue()
		assertThat(over.newPrice()).isEqualTo(10)
		assertThat(over.priceText()).isEqualTo("ISO price at Mu Herculis: 11 → 10 HT")
	}

	@Test
	fun shouldMoveThePriceByTheRollAloneWhenNobodyTrades() {
		// S05-2, R35: no offers; counter 1 + 12 = 13, modifier -4: 11 → 7.
		val resolution = Resolution.start(good, 11, emptyList(), null, emptyList(), initiative).startCounterOnceDecided(FakeDice.twoD6(12))

		assertThat(resolution.newPrice()).isEqualTo(7)
		assertThat(resolution.isFinished()).isTrue()
	}

	@Test
	fun shouldUseTheResolutionsOwnStartPriceForEveryMaxAndThePrice() {
		// R34, R35: a buy at 15 is +4 over the board's 11 (target -11) but only +1 over 14, where the price moved in an
		// earlier turn (target -3). At counter 3 it may buy 6, not 14; after 6 the counter is -3: 14 + modifier(-3) = 15.
		val buyAt15 = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 15)
		val resolution = Resolution.start(good, 14, listOf(buyAt15), null, emptyList(), initiative).startCounterOnceDecided(FakeDice.twoD6(2))

		assertThat(resolution.awaitingTrade()).isEqualTo(AwaitedTrade(buyAt15, 6))
		assertThat(resolution.withTrade(6, null).newPrice()).isEqualTo(15)
	}

	@Test
	fun shouldClampTheNewPriceToOneAndTwenty() {
		// S05-1, no offers: Gamma Leporis POLY (S/D +1) at 2 HT, roll 12: counter 13, modifier -4, so -2 → 1 HT. Tau Ceti
		// POLY (S/D -10) at 19 HT, roll 2: counter -8, modifier +3, so 22 → 20 HT.
		val low = Resolution.start(MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY), 2, emptyList(), null, emptyList(), initiative)
			.startCounterOnceDecided(FakeDice.twoD6(12))
		val high = Resolution.start(MarketGood(StarSystem.TAU_CETI, Goods.POLY), 19, emptyList(), null, emptyList(), initiative)
			.startCounterOnceDecided(FakeDice.twoD6(2))

		assertThat(low.newPrice()).isEqualTo(1)
		assertThat(low.priceText()).isEqualTo("POLY price at Gamma Leporis: 2 → 1 HT")
		assertThat(high.newPrice()).isEqualTo(20)
	}

	@Test
	fun shouldAskWholesalersWithoutAnOfferAndTheDictatorInInitiativeOrderOnceTradingIsOver() {
		// R37, S05-4, S05-6: Vega is Wholesaler too, but has an offer here, so only Kessler and Altair are asked.
		val started = workedExample("altair@example.com", listOf("vega@example.com", "kessler@example.com"))

		assertThat(started.wholesalers).containsExactly("kessler@example.com", "altair@example.com")
		val over = started.withDictatorPassed().startCounterOnceDecided(FakeDice.twoD6(2)).withTrade(4, null).withTrade(5, null)
		assertThat(started.withDictatorPassed().startCounterOnceDecided(FakeDice.twoD6(2)).awaitingWholesaler()).isNull()
		assertThat(over.awaitingWholesaler()).isEqualTo("kessler@example.com")
		assertThat(over.isFinished()).isFalse()
		assertThat(over.wholesaleRole("kessler@example.com")).isEqualTo("Wholesaler")
		assertThat(over.wholesaleRole("altair@example.com")).isEqualTo("Dictator")
	}

	@Test
	fun shouldRecordEachWholesaleAtTheNewPriceWithoutMovingItAndFinishAfterTheLast() {
		// The worked example's step 6: Kessler buys 3 u at 10 HT, Altair sells 2 u at 10 HT; the price stays 10.
		val over = workedExample("altair@example.com", listOf("kessler@example.com")).withDictatorPassed()
			.startCounterOnceDecided(FakeDice.twoD6(2)).withTrade(4, null).withTrade(5, null)

		assertThat(
			over.wholesaleOffer(OfferType.BUY)
		).isEqualTo(Offer("kessler@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 10))
		val kessler = over.withWholesale(OfferType.BUY, 3)
		val altair = kessler.withWholesale(OfferType.SELL, 2)

		assertThat(kessler.awaitingWholesaler()).isEqualTo("altair@example.com")
		assertThat(altair.wholesales).containsExactly(
			Wholesale("kessler@example.com", OfferType.BUY, 3, 10),
			Wholesale("altair@example.com", OfferType.SELL, 2, 10)
		)
		assertThat(altair.newPrice()).isEqualTo(10)
		assertThat(altair.counter()).isEqualTo(4)
		assertThat(altair.isFinished()).isTrue()
		assertThat(over.withWholesale(null, 0).wholesales.single().terms()).isEqualTo("passed on wholesale")
		assertThatThrownBy { altair.withWholesale(null, 0) }.hasMessage("No wholesale decision is pending.")
		assertThatThrownBy { altair.wholesaleOffer(OfferType.BUY) }.hasMessage("No wholesale decision is pending.")
	}

	@Test
	fun shouldDescribeAWholesaleWithItsTotal() {
		// S05-5, like every trade line (E05-S04-6)
		assertThat(Wholesale("k", OfferType.BUY, 3, 10).terms()).isEqualTo("bought 3 u at 10 HT each, paid 30 HT")
		assertThat(Wholesale("k", OfferType.SELL, 2, 10).terms()).isEqualTo("sold 2 u at 10 HT each, received 20 HT")
	}

	@Test
	fun shouldReadAResolutionSavedBeforeTheCounterAsFinished() {
		// E05-S02 finished a resolution with no counter once the Dictator had decided.
		val saved = Resolution(good, 11, listOf(vega), null, false, null, emptyList(), emptyList(), emptyList(), emptyList())

		assertThat(saved.isFinished()).isTrue()
	}

	@Test
	fun shouldNotRollWhileTheDictatorIsAskedNorRollTwice() {
		// R38: the Dictator decides before the roll; FakeDice fails if a roll is taken
		val asked = start(listOf(vega), "altair@example.com")
		val started = start(listOf(vega), null).startCounterOnceDecided(FakeDice.twoD6(2))

		assertThat(asked.startCounterOnceDecided(FakeDice.twoD6())).isEqualTo(asked)
		assertThat(asked.marketText()).isNull()
		assertThat(asked.awaitingTrade()).isNull()
		assertThat(started.startCounterOnceDecided(FakeDice.twoD6())).isEqualTo(started)
	}

	@Test
	fun shouldShowAZeroCounterAsFavouringBuyers() {
		val zero = Resolution(good, 11, emptyList(), null, false, 0, emptyList(), initiative, emptyList(), emptyList())

		assertThat(zero.marketText()).isEqualTo("market 0 → buyers first")
	}

	@Test
	fun shouldSayWhatTheMarketAllowedAndWhatTookPlace() {
		// E05-S04-6
		assertThat(Trade(vega, 12, 10, null).terms()).isEqualTo("could buy up to 12 u: bought 10 u at 11 HT each, paid 110 HT")
		assertThat(Trade(orion, 10, 4, null).terms()).isEqualTo("could sell up to 10 u: sold 4 u at 10 HT each, received 40 HT")
		assertThat(Trade(vega, 3, 0, null).terms()).isEqualTo("could buy up to 3 u: bought nothing")
	}
}
