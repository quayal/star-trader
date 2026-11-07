package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E05-S02: offers are disclosed and the Dictator may act (R38). E05-S03: the counter resolves the offers (R34).
class ResolutionTest {

	private val good = MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)
	private val vega = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)
	private val orion = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 10)
	private val otherGood = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.CHIPS, OfferType.SELL, 4)
	private val otherSystem = Offer("orion@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 6)

	@Test
	fun shouldDiscloseOnlyTheOffersForThatGoodAtThatSystemInTheOrderTheyWerePlaced() {
		val resolution = Resolution.start(good, listOf(vega, otherGood, otherSystem, orion), null)

		assertThat(resolution.offers).containsExactly(vega, orion)
	}

	@Test
	fun shouldFinishAtOnceWithNoDictator() {
		// S02-4
		val resolution = Resolution.start(good, listOf(vega), null)

		assertThat(resolution.awaitingDictator()).isNull()
		assertThat(resolution.isFinished()).isTrue()
		assertThat(resolution.dictatorOffer()).isNull()
	}

	@Test
	fun shouldWaitForTheDictatorUntilTheyDecide() {
		// S02-2
		val resolution = Resolution.start(good, listOf(vega), "altair@example.com")
		val dictators = Offer("altair@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 10)

		assertThat(resolution.awaitingDictator()).isEqualTo("altair@example.com")
		assertThat(resolution.isFinished()).isFalse()
		assertThat(resolution.withDictatorOffer(dictators).offers).containsExactly(vega, dictators)
		assertThat(resolution.withDictatorOffer(dictators).dictatorOffer()).isEqualTo(dictators)
		assertThat(resolution.withDictatorOffer(dictators).isFinished()).isTrue()
		assertThat(resolution.withDictatorPassed().offers).containsExactly(vega)
		assertThat(resolution.withDictatorPassed().isFinished()).isTrue()
	}

	@Test
	fun shouldAskTheDictatorOnce() {
		val passed = Resolution.start(good, emptyList(), "altair@example.com").withDictatorPassed()

		assertThatThrownBy { passed.withDictatorPassed() }.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("No Dictator decision is pending.")
		assertThatThrownBy { Resolution.start(good, emptyList(), null).withDictatorOffer(vega) }
			.hasMessage("No Dictator decision is pending.")
	}

	@Test
	fun shouldDescribeAnOfferForThePublicLog() {
		assertThat(vega.terms()).isEqualTo("buy at 11 HT")
		assertThat(orion.terms()).isEqualTo("sell at 10 HT")
	}

	// Mu Herculis ISO starts at 11 HT with S/D +1; Tau Ceti ISO at 4 HT with S/D -9.
	private val initiative = listOf("vega@example.com", "orion@example.com", "kessler@example.com")
	private val kessler = Offer("kessler@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 10)
	private val tauCetiIso = MarketGood(StarSystem.TAU_CETI, Goods.ISO)

	@Test
	fun shouldResolveTheWorkedExample() {
		// The spec's worked example: roll 2, counter 3. Vega wins and buys 3 - (-1) = 4 (counter -1); Orion sells
		// 4 - (-1) = 5 (counter 4); nobody is left.
		val resolution = Resolution.start(good, listOf(vega, orion), null).startCounterOnceDecided(FakeDice.twoD6(2), initiative)

		assertThat(resolution.startingCounter).isEqualTo(3)
		assertThat(resolution.trades).containsExactly(Trade(vega, 4), Trade(orion, 5))
		assertThat(resolution.counter()).isEqualTo(4)
		assertThat(resolution.isFinished()).isTrue()
		assertThat(resolution.marketText()).isEqualTo("market +3 → buyers first")
		assertThat(resolution.endText()).isEqualTo("No one else can trade")
	}

	@Test
	fun shouldLetAThirdOfferTradeWhatTheCounterStillAllows() {
		// The worked example's variant: Kessler buying at 10 (target 2) buys 4 - 2 = 2 after Orion's sell.
		val resolution = Resolution.start(good, listOf(kessler, orion, vega), null).startCounterOnceDecided(FakeDice.twoD6(2), initiative)

		assertThat(resolution.trades).containsExactly(Trade(vega, 4), Trade(orion, 5), Trade(kessler, 2))
		assertThat(resolution.counter()).isEqualTo(2)
	}

	@Test
	fun shouldLetTheLowestSellTradeFirstWhenTheCounterIsNegative() {
		// S03-2: counter -9 + 5 = -4, so the sell at 4 (target 1) trades first, 5 u, though the buy at 11 (saturated,
		// target -18) allows 14. Then the most units at each counter: the buy 19 (to -18), then the sell at 5 (target -2) 16.
		val sellAt4 = Offer("orion@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 4)
		val sellAt5 = Offer("kessler@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 5)
		val buyAt11 = Offer("vega@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 11)

		val resolution = Resolution.start(tauCetiIso, listOf(buyAt11, sellAt5, sellAt4), null)
			.startCounterOnceDecided(FakeDice.twoD6(5), initiative)

		assertThat(resolution.trades).containsExactly(Trade(sellAt4, 5), Trade(buyAt11, 19), Trade(sellAt5, 16))
		assertThat(resolution.counter()).isEqualTo(-2)
		assertThat(resolution.marketText()).isEqualTo("market \u22124 → sellers first")
	}

	@Test
	fun shouldLetTheOtherSideTradeFirstWhenTheWinningSideHasNoOfferThatCan() {
		// Counter 3 favours buyers, but there are none: Orion's sell (target 4) still sells 1.
		val resolution = Resolution.start(good, listOf(orion), null).startCounterOnceDecided(FakeDice.twoD6(2), initiative)

		assertThat(resolution.trades).containsExactly(Trade(orion, 1))
	}

	@Test
	fun shouldBreakABuyerSellerTieByTheCountersSignThenInitiative() {
		// S03-3. At Mu Herculis ISO with the counter at 2, a buy at 12 (target -3) and a sell at 9 (target 7) both allow
		// 5: the counter is 0 or more, so the buyer. At Tau Ceti ISO with the counter at -2, a buy at 6 (target -5) and a
		// sell at 4 (target 1) both allow 3: the seller.
		val buyAt12 = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 12)
		val sellAt9 = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 9)
		val atTwo = Resolution(good, listOf(vega, sellAt9, buyAt12), null, false, 3, listOf(Trade(vega, 1)))
		val buyAt6 = Offer("vega@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 6)
		val sellAt4 = Offer("orion@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 4)
		val sellAt3 = Offer("kessler@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 3)
		val atMinusTwo = Resolution(tauCetiIso, listOf(sellAt3, buyAt6, sellAt4), null, false, -4, listOf(Trade(sellAt3, 2)))

		assertThat(atTwo.nextTrade(initiative)).isEqualTo(Trade(buyAt12, 5))
		assertThat(atMinusTwo.nextTrade(initiative)).isEqualTo(Trade(sellAt4, 3))
	}

	@Test
	fun shouldStillStartTheCounterWithNoOfferThatCanTrade() {
		// S03-7: no offers, or one priced out of the market (a buy at 9, target 5, at counter 3), and nobody trades.
		val buyAt9 = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 9)

		listOf(emptyList(), listOf(buyAt9)).forEach { offers ->
			val resolution = Resolution.start(good, offers, null).startCounterOnceDecided(FakeDice.twoD6(2), initiative)

			assertThat(resolution.startingCounter).isEqualTo(3)
			assertThat(resolution.trades).isEmpty()
			assertThat(resolution.endText()).isEqualTo("No one can trade")
		}
	}

	@Test
	fun shouldNotRollWhileTheDictatorIsAskedNorRollTwice() {
		// R38: the Dictator decides before the roll; FakeDice fails if a roll is taken
		val asked = Resolution.start(good, listOf(vega), "altair@example.com")
		val started = Resolution.start(good, listOf(vega), null).startCounterOnceDecided(FakeDice.twoD6(2), initiative)

		assertThat(asked.startCounterOnceDecided(FakeDice.twoD6(), initiative)).isEqualTo(asked)
		assertThat(asked.marketText()).isNull()
		assertThat(started.startCounterOnceDecided(FakeDice.twoD6(), initiative)).isEqualTo(started)
	}

	@Test
	fun shouldShowAZeroCounterAsFavouringBuyers() {
		val zero = Resolution(good, emptyList(), null, false, 0, emptyList())

		assertThat(zero.marketText()).isEqualTo("market 0 → buyers first")
	}

	@Test
	fun shouldDescribeATradeForThePublicLog() {
		assertThat(Trade(vega, 4).terms()).isEqualTo("bought 4 u at 11 HT each")
		assertThat(Trade(orion, 5).terms()).isEqualTo("sold 5 u at 10 HT each")
	}
}
