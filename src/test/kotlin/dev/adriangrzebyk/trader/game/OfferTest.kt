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
}
