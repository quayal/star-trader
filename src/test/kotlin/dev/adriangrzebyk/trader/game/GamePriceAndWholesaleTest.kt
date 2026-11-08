package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E05-S05: the price changes once each good's trades are over (R35), then its Wholesalers and Dictator trade (R37).
class GamePriceAndWholesaleTest {

	private fun dice() = FakeDice.twoD6(*IntArray(MarketGood.everyOnBoard().size) { 7 })

	private val gammaLeporisPoly = MarketGood(GAMMA_LEPORIS, Goods.POLY)

	// Gamma Leporis POLY (S/D +1, 2 HT): Ada (Orion Freight) is Dictator, Kim (Altair Trade Co.) Wholesaler with no offer
	// for it, and Grace (Vega Lines) buys at 11. Grace's Meridian and Kim's Bright Reach are in port there. Initiative:
	// Grace, Kim, Ada.
	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
		.copy(marketPositions = listOf(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.DICTATOR)))
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Altair Trade Co.")
		.copy(marketPositions = listOf(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.WHOLESALER)))

	private val trading = Game.lobby("game-1", 3, ada)
		.addPlayer(grace)
		.addPlayer(kim)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, GAMMA_LEPORIS to MU_HERCULIS))
		.placeOffer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11)
		.advancePhase(FakeDice(1, 2, 1, 1, 2))
		.pickTurnOrderPlace("grace@example.com", 1)
		.pickTurnOrderPlace("kim@example.com", 2)
		.finishJumping("grace@example.com")
		.finishJumping("kim@example.com")
		.finishJumping("ada@example.com")

	// Ada passes before the roll; roll 2, counter 3. Grace buys 12 (counter -9), and nobody else can trade: the price moves
	// from 2 by modifier(-9) = +4 to 6 HT, and Kim, then Ada, are asked.
	private val priceChanged = trading.resolveNextGood("grace@example.com", dice())
		.passAsDictator("ada@example.com", FakeDice.twoD6(2))
		.trade("grace@example.com", 12, listOf(ShipLoad("Meridian", 12)))

	@Test
	fun shouldChangeThePriceOnceTradingIsOverAndLogIt() {
		// S05-1, S05-2, S05-3: the game's price moves the moment no offer can trade; other goods keep theirs
		assertThat(priceChanged.prices.priceOf(gammaLeporisPoly)).isEqualTo(6)
		assertThat(priceChanged.prices.priceOf(MarketGood(TAU_CETI, Goods.POLY))).isEqualTo(6)
		assertThat(priceChanged.prices.priceOf(MarketGood(MU_HERCULIS, Goods.ISO))).isEqualTo(11)
		assertThat(priceChanged.log.takeLast(2).map { it.text }).containsExactly(
			"No one else can trade at Gamma Leporis POLY",
			"POLY price at Gamma Leporis: 2 → 6 HT"
		)
	}

	@Test
	fun shouldChangeThePriceEvenWhenNobodyTrades() {
		// S05-2: Tau Ceti POLY (S/D -10, 6 HT) has no offers; roll 7, counter -3, modifier +1: 6 → 7 HT
		val order = listOf(MarketGood(TAU_CETI, Goods.POLY)) + trading.turn!!.tradingQueue.minus(MarketGood(TAU_CETI, Goods.POLY))

		val game = trading.reorderTradingQueue("grace@example.com", order).resolveNextGood("grace@example.com", dice())

		assertThat(game.prices.priceOf(MarketGood(TAU_CETI, Goods.POLY))).isEqualTo(7)
		assertThat(game.log.last().text).isEqualTo("POLY price at Tau Ceti: 6 → 7 HT")
		assertThat(game.turn!!.resolvedGoods).containsExactly(MarketGood(TAU_CETI, Goods.POLY))
	}

	@Test
	fun shouldStartEachResolutionFromTheGamesPriceNow() {
		// R34, R35: a price that moved in an earlier turn is the next start price
		val moved = trading.copy(prices = MarketPrices.board().withPrice(gammaLeporisPoly, 9))

		val game = moved.resolveNextGood("grace@example.com", dice())

		assertThat(game.turn!!.resolution!!.startPrice).isEqualTo(9)
	}

	@Test
	fun shouldAskTheWholesalerWithoutAnOfferThenTheDictatorInInitiativeOrder() {
		// R37, S05-4, S05-6, S01-8 (D09)
		assertThat(priceChanged.turn!!.wholesalerToDecide()).isEqualTo("kim@example.com")
		assertThat(priceChanged.turn!!.resolvedGoods).isEmpty()
		val waiting = "Waiting for Altair Trade Co. (Wholesaler) to trade up to 5 u at 6 HT, or pass."
		assertThatThrownBy { priceChanged.resolveNextGood("grace@example.com", dice()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage(waiting)
		assertThatThrownBy { priceChanged.passWholesale("ada@example.com") }.isInstanceOf(NotYourTurnException::class.java).hasMessage(waiting)
		assertThatThrownBy { priceChanged.passWholesale("someone@example.com") }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldLetTheWholesalerTradeAtTheNewPriceWithoutMovingItThenTheDictatorPass() {
		// R37, S05-5: Kim buys 3 POLY at 6 HT into Bright Reach; then Ada passes and the good has resolved
		val kimBought = priceChanged.wholesale("kim@example.com", OfferType.BUY, 3, listOf(ShipLoad("Bright Reach", 3)))
		val adaPassed = kimBought.passWholesale("ada@example.com")

		val kimNow = kimBought.players.first { it.email == "kim@example.com" }
		assertThat(kimNow.money).isEqualTo(282)
		assertThat(kimNow.ship("Bright Reach")!!.holding(Goods.POLY)).isEqualTo(3)
		assertThat(kimBought.prices).isEqualTo(priceChanged.prices)
		assertThat(kimBought.turn!!.wholesalerToDecide()).isEqualTo("ada@example.com")
		assertThat(adaPassed.turn!!.resolvedGoods).containsExactly(gammaLeporisPoly)
		assertThat(adaPassed.prices.priceOf(gammaLeporisPoly)).isEqualTo(6)
		assertThat(adaPassed.log.drop(priceChanged.log.size).map { it.text }).containsExactly(
			"Altair Trade Co. (Wholesaler) bought 3 u at 6 HT each, paid 18 HT",
			"Orion Freight (Dictator) passed on wholesale"
		)
		assertThat(adaPassed.resolveNextGood("grace@example.com", dice()).turn!!.resolvedGoods).hasSize(2)
	}

	@Test
	fun shouldCapAWholesaleAtFiveUnitsMoneyAndCargo() {
		// R37, R12, R10: up to 5 u; Kim has no POLY to sell; a type is needed
		assertThatThrownBy { priceChanged.wholesale("kim@example.com", OfferType.BUY, 6, listOf(ShipLoad("Bright Reach", 6))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Enter a whole number from 1 to 5.")
		assertThatThrownBy { priceChanged.wholesale("kim@example.com", OfferType.SELL, 1, listOf(ShipLoad("Bright Reach", 1))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("You have no POLY in your ships in port at Gamma Leporis, so you can't sell any.")
		assertThatThrownBy { priceChanged.wholesale("kim@example.com", null, 1, listOf(ShipLoad("Bright Reach", 1))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Choose Buy or Sell, or pass.")
		assertThatThrownBy { trading.passWholesale("kim@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("No wholesale decision is pending.")
	}
}
