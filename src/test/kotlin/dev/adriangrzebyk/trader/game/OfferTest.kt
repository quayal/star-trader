package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OfferTest {

	@Test
	fun shouldPlaceAnOfferForAGoodTradedAtTheSystem() {
		val offer = Offer.place("ada@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.BUY, 20)

		assertThat(offer).isEqualTo(Offer("ada@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.BUY, 20))
	}

	@Test
	fun shouldRefuseAGoodNotTradedAtTheSystem() {
		assertThatThrownBy { Offer.place("ada@example.com", StarSystem.GAMMA_LEPORIS, Goods.CHIPS, OfferType.BUY, 8) }
			.isInstanceOf(InvalidOfferException::class.java)
			.hasMessage("CHIPS isn't traded at Gamma Leporis.")
	}

	@Test
	fun shouldRefuseAPriceOutsideOneToTwenty() {
		listOf(0, 21, null).forEach { price ->
			assertThatThrownBy { Offer.place("ada@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.SELL, price) }
				.isInstanceOf(InvalidOfferException::class.java)
				.hasMessage("Price must be a whole number from 1 to 20.")
		}
	}

	@Test
	fun shouldAllowABuyTheCounterLessTheBuyTargetOfItsPrice() {
		// The spec's worked example: Mu Herculis ISO starts at 11 HT; Vega buys at 11 (target -1) with the counter at 3.
		val vega = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)

		assertThat(vega.maxUnits(3, 11)).isEqualTo(4)
		assertThat(vega.maxUnits(-1, 11)).isEqualTo(0)
	}

	@Test
	fun shouldAllowASellTheSellTargetOfItsPriceLessTheCounter() {
		// Orion sells at 10 (target 4).
		val orion = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 10)

		assertThat(orion.maxUnits(-1, 11)).isEqualTo(5)
		assertThat(orion.maxUnits(2, 11)).isEqualTo(2)
		assertThat(orion.maxUnits(4, 11)).isEqualTo(0)
	}

	@Test
	fun shouldSaturateAnExtremePrice() {
		// R15: a buy 9 above the start price counts as +7 (target -18), a sell 9 below as -6 (target +18).
		val buy = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 20)
		val sell = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 2)

		assertThat(buy.maxUnits(0, 11)).isEqualTo(18)
		assertThat(sell.maxUnits(0, 11)).isEqualTo(18)
	}
}
