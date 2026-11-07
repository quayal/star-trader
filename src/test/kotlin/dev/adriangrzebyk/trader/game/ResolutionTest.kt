package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E05-S02: offers are disclosed and the Dictator may act (R38).
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
}
