package dev.adriangrzebyk.trader.trading

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import dev.adriangrzebyk.trader.trading.adapter.memory.InMemoryTradeOfferBook

class TradeDeskTest {

	@Test
	fun shouldRejectGoodsNotTradedInTheChosenSystem() {
		val tradeDesk = TradeDesk(InMemoryTradeOfferBook(), FakeDiceRoller(roll = 8))

		assertThatThrownBy {
			tradeDesk.submit("Adrian", StarSystem.GAMMA_LEPORIS, Goods.CHIPS, OfferType.BUY, price = 5, turn = 1)
		}.isInstanceOf(InvalidTradeOfferException::class.java)
	}

	@Test
	fun shouldRejectPriceOutsideOneToTwenty() {
		val tradeDesk = TradeDesk(InMemoryTradeOfferBook(), FakeDiceRoller(roll = 8))

		assertThatThrownBy {
			tradeDesk.submit("Adrian", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, price = 21, turn = 1)
		}.isInstanceOf(InvalidTradeOfferException::class.java)
	}

	@Test
	fun shouldStoreASubmittedOfferAndMakeItListable() {
		val tradeDesk = TradeDesk(InMemoryTradeOfferBook(), FakeDiceRoller(roll = 8))

		val offer = tradeDesk.submit("Adrian", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, price = 1, turn = 1)

		assertThat(tradeDesk.allOffers()).containsExactly(offer)
	}

	@Test
	fun shouldReturnNullWhenNoOffersExistForTheRequestedTurnSystemAndGoods() {
		val tradeDesk = TradeDesk(InMemoryTradeOfferBook(), FakeDiceRoller(roll = 8))

		assertThat(tradeDesk.resolve(turn = 1, system = StarSystem.GAMMA_LEPORIS, goods = Goods.POLY)).isNull()
	}

	@Test
	fun shouldResolveTheOnlyOfferUsingTheRolledDice() {
		val tradeDesk = TradeDesk(InMemoryTradeOfferBook(), FakeDiceRoller(roll = 8))
		tradeDesk.submit("Adrian", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, price = 1, turn = 1)

		val result = tradeDesk.resolve(turn = 1, system = StarSystem.GAMMA_LEPORIS, goods = Goods.POLY)

		assertThat(result).isNotNull
		assertThat(result!!.tradeOffer?.player?.name).isEqualTo("Adrian")
		assertThat(result.amount).isEqualTo(7)
	}
}
