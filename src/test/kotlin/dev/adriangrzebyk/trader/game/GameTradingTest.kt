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

// E05-S01: the first initiative player orders and triggers the resolutions (R25, R36).
class GameTradingTest {

	/** Enough 2d6 rolls for every good on the board to resolve (R36), all 7. */
	private fun dice() = FakeDice.twoD6(*IntArray(MarketGood.everyOnBoard().size) { 7 })

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	// Initiative Grace, Ada: Ada rolls 1 and Grace 2 on d2, with no bids.
	private val shipJumps = Game.lobby("game-1", 3, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))
		.advancePhase(FakeDice(1, 2))
		.pickTurnOrderPlace("grace@example.com", 1)

	private val trading = shipJumps.finishJumping("grace@example.com").finishJumping("ada@example.com")

	@Test
	fun shouldQueueEveryGoodWhenTheLastJumpSlotEnds() {
		assertThat(shipJumps.turn!!.initiative).containsExactly("grace@example.com", "ada@example.com")
		assertThat(trading.turn!!.phase).isEqualTo(TurnPhase.TRADING)
		assertThat(trading.turn!!.tradingQueue).isEqualTo(MarketGood.everyOnBoard())
	}

	@Test
	fun shouldResolveTheNextGoodAndLogWhoChoseIt() {
		// S01-4, S01-5
		val game = trading.resolveNextGood("grace@example.com", dice())

		assertThat(game.turn!!.resolvedGoods).containsExactly(MarketGood(GAMMA_LEPORIS, Goods.POLY))
		// S03-7: with no offers the counter still starts (S/D +1, roll 7), and nobody can trade
		assertThat(game.log.drop(trading.log.size)).containsExactly(
			LogEntry(1, "Resolving: Gamma Leporis POLY (chosen by Vega Lines)"),
			LogEntry(1, "No offers for Gamma Leporis POLY"),
			LogEntry(1, "Gamma Leporis POLY: market +8 → buyers first"),
			LogEntry(1, "No one can trade at Gamma Leporis POLY"),
			LogEntry(1, "POLY price at Gamma Leporis: 2 → 1 HT")
		)
	}

	@Test
	fun shouldResolveInTheOrderTheFirstInInitiativeChose() {
		// S01-3: reordering isn't logged; the resolution is
		val order = listOf(MarketGood(MU_HERCULIS, Goods.ISO)) + trading.turn!!.tradingQueue.minus(MarketGood(MU_HERCULIS, Goods.ISO))

		val reordered = trading.reorderTradingQueue("grace@example.com", order)
		val game = reordered.resolveNextGood("grace@example.com", dice())

		assertThat(reordered.log).isEqualTo(trading.log)
		assertThat(game.log.drop(trading.log.size).first().text).isEqualTo("Resolving: Mu Herculis ISO (chosen by Vega Lines)")
	}

	@Test
	fun shouldRefuseAnyoneButTheFirstInInitiative() {
		// R25, R04
		assertThatThrownBy { trading.resolveNextGood("ada@example.com", dice()) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("Order decided by Vega Lines (first in initiative).")
		assertThatThrownBy { trading.reorderTradingQueue("ada@example.com", trading.turn!!.tradingQueue.reversed()) }
			.isInstanceOf(NotYourTurnException::class.java)
	}

	@Test
	fun shouldRefuseSomeoneOutsideTheGame() {
		assertThatThrownBy { trading.resolveNextGood("kim@example.com", dice()) }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldRefuseToResolveBeforeTrading() {
		assertThatThrownBy { shipJumps.resolveNextGood("grace@example.com", dice()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Trading hasn't started.")
	}

	// E05-S02: Ada is Dictator of POLY at Gamma Leporis, the first good to resolve; Grace and Kim placed offers for it.
	private val kim = Player.newcomer("kim@example.com", "Kim", "Altair Trade Co.")
	private val dictatorOfPoly = ada.copy(marketPositions = listOf(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.DICTATOR)))
	private val withDictator = Game.lobby("game-2", 3, dictatorOfPoly)
		.addPlayer(grace)
		.addPlayer(kim)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, TAU_CETI to MU_HERCULIS))
		.placeOffer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11)
		.placeOffer("kim@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.SELL, 10)
		.placeOffer("kim@example.com", TAU_CETI, Goods.POLY, OfferType.SELL, 9)
		// Ada 1, Grace 2, Kim 1, then Ada 1 and Kim 2 on the tie: Grace, Kim, Ada.
		.advancePhase(FakeDice(1, 2, 1, 1, 2))
		.pickTurnOrderPlace("grace@example.com", 1)
		.pickTurnOrderPlace("kim@example.com", 2)
		.finishJumping("grace@example.com")
		.finishJumping("kim@example.com")
		.finishJumping("ada@example.com")

	private val askingDictator = withDictator.resolveNextGood("grace@example.com", dice())

	@Test
	fun shouldDiscloseEveryOfferForTheGoodAndAskItsDictator() {
		// S02-1, S02-2
		val resolution = askingDictator.turn!!.resolution!!

		assertThat(withDictator.turn!!.initiative).containsExactly("grace@example.com", "kim@example.com", "ada@example.com")
		assertThat(resolution.offers.map { it.playerEmail }).containsExactly("grace@example.com", "kim@example.com")
		assertThat(resolution.awaitingDictator()).isEqualTo("ada@example.com")
		assertThat(askingDictator.turn!!.resolvedGoods).isEmpty()
		assertThat(askingDictator.log.last()).isEqualTo(
			LogEntry(1, "Offers for Gamma Leporis POLY: Vega Lines — buy at 11 HT; Altair Trade Co. — sell at 10 HT")
		)
	}

	@Test
	fun shouldRefuseTheNextGoodWhileTheDictatorDecides() {
		// S01-4, S01-8
		assertThatThrownBy { askingDictator.resolveNextGood("grace@example.com", dice()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Waiting for Orion Freight (Dictator) to decide.")
	}

	@Test
	fun shouldDiscloseTheDictatorsOfferLikeTheOthersAndLogIt() {
		// S02-2, S02-3
		val game = askingDictator.placeDictatorOffer("ada@example.com", OfferType.SELL, 9, dice())

		val resolution = game.turn!!.resolution!!
		assertThat(resolution.offers.last()).isEqualTo(Offer("ada@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.SELL, 9))
		assertThat(resolution.awaitingDictator()).isNull()
		assertThat(game.log).contains(LogEntry(1, "Orion Freight (Dictator) offers to sell at 9 HT"))
		assertThat(game.turn!!.offersOf("ada@example.com")).isEmpty()
	}

	@Test
	fun shouldLetTheDictatorPass() {
		// S02-2
		val game = askingDictator.passAsDictator("ada@example.com", dice())

		assertThat(game.turn!!.resolution!!.offers).hasSize(2)
		assertThat(game.turn!!.resolution!!.awaitingDictator()).isNull()
		assertThat(game.log).contains(LogEntry(1, "Orion Freight (Dictator) passed"))
	}

	@Test
	fun shouldStartTheCounterAfterTheDictatorDecidesAndWaitForTheWinner() {
		// S03-1, S03-2, S03-6. Gamma Leporis POLY: S/D +1, 2 HT. Vega's buy at 11 and Altair's sell at 10 saturate (R15):
		// targets -18 for both. Roll 2: counter 3, so Vega may buy up to 3 - (-18) = 21, and the game waits for her.
		assertThat(askingDictator.turn!!.resolution!!.startingCounter).isNull()

		val game = askingDictator.passAsDictator("ada@example.com", FakeDice.twoD6(2))

		assertThat(
			game.turn!!.tradeAwaited()
		).isEqualTo(AwaitedTrade(Offer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11), 21))
		assertThat(game.turn!!.resolvedGoods).isEmpty()
		assertThat(game.log.drop(askingDictator.log.size)).containsExactly(
			LogEntry(1, "Orion Freight (Dictator) passed"),
			LogEntry(1, "Gamma Leporis POLY: market +3 → buyers first")
		)
	}

	// E05-S04: Grace (Vega Lines) may buy up to 21 POLY at Gamma Leporis, where her ship Meridian is.
	private val gracesTurnToTrade = askingDictator.passAsDictator("ada@example.com", FakeDice.twoD6(2))

	@Test
	fun shouldLetTheTraderChooseAnAmountAndShipsThenLogTheTrade() {
		// S04-1, S04-3, S04-4, S04-6, S04-7: 12 POLY at 11 HT costs 132; Meridian has 10 free spaces, so 2 are lost. Then
		// the counter is -9 and Altair's sell at 10 (target -18) allows nothing, so trading is over: the price moves from
		// 2 by modifier(-9) = +4 to 6 HT (E05-S05-1), and Ada, the Dictator, is asked to trade wholesale (R37). Trading 12 u
		// rolls for a position (E05-S06): 7 is at most 12, so Grace is Retailer from turn 2.
		val game = gracesTurnToTrade.trade("grace@example.com", 12, listOf(ShipLoad("Meridian", 12)), FakeDice.twoD6(7))

		val grace = game.players.first { it.email == "grace@example.com" }
		assertThat(grace.money).isEqualTo(168)
		assertThat(grace.ship("Meridian")!!.holding(Goods.POLY)).isEqualTo(10)
		assertThat(
			game.turn!!.resolution!!.trades
		).containsExactly(
			Trade(
				Offer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11),
				21,
				12,
				PositionRoll(7, MarketTier.RETAILER, emptyList())
			)
		)
		assertThat(game.turn!!.resolution!!.awaitingWholesaler()).isEqualTo("ada@example.com")
		assertThat(game.log.drop(gracesTurnToTrade.log.size)).containsExactly(
			LogEntry(1, "Vega Lines could buy up to 21 u: bought 12 u at 11 HT each, paid 132 HT"),
			LogEntry(1, "Vega Lines traded 12 u — rolled 7: now Retailer of POLY at Gamma Leporis from turn 2"),
			LogEntry(1, "No one else can trade at Gamma Leporis POLY"),
			LogEntry(1, "POLY price at Gamma Leporis: 2 → 6 HT")
		)
		val passed = game.passWholesale("ada@example.com")
		assertThat(passed.turn!!.resolvedGoods).containsExactly(MarketGood(GAMMA_LEPORIS, Goods.POLY))
		assertThat(passed.resolveNextGood("grace@example.com", dice()).turn!!.resolvedGoods).hasSize(2)
	}

	@Test
	fun shouldRefuseTheNextGoodAndOtherPlayersWhileTheTraderDecides() {
		// S01-4, S01-8 (D09); R04
		assertThatThrownBy { gracesTurnToTrade.resolveNextGood("grace@example.com", dice()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Vega Lines can buy up to 21 u — waiting for their decision.")
		assertThatThrownBy { gracesTurnToTrade.trade("kim@example.com", 1, emptyList(), FakeDice.twoD6()) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("Vega Lines can buy up to 21 u — waiting for their decision.")
		assertThatThrownBy { askingDictator.trade("grace@example.com", 1, emptyList(), FakeDice.twoD6()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("No trade is waiting for a decision.")
	}

	@Test
	fun shouldRefuseAnAmountOutsideOneToTheTradersMaxAndTradeNothing() {
		// S04-1
		assertThatThrownBy { gracesTurnToTrade.trade("grace@example.com", 22, listOf(ShipLoad("Meridian", 22)), FakeDice.twoD6()) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Enter a whole number from 1 to 21.")
	}

	@Test
	fun shouldLetATraderWhoCanAffordNothingAcknowledgeIt() {
		// S04-5: Grace bid all her 300 HT for initiative, so she can't afford POLY at 11 HT. The game waits for her to
		// acknowledge; then her offer is done with none, the counter stays at 3, Altair's sell allows nothing, and nobody
		// traded. The log says what the market allowed, never why she traded nothing (R29).
		val broke = Game.lobby("game-3", 3, dictatorOfPoly)
			.addPlayer(grace)
			.addPlayer(kim)
			.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, TAU_CETI to MU_HERCULIS))
			.placeOffer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11)
			.placeInitiativeBid("grace@example.com", 300)
			.advancePhase(FakeDice(1, 2, 1, 1, 2))
			.pickTurnOrderPlace("grace@example.com", 1)
			.pickTurnOrderPlace("kim@example.com", 2)
			.finishJumping("grace@example.com")
			.finishJumping("kim@example.com")
			.finishJumping("ada@example.com")
			.resolveNextGood("grace@example.com", dice())
			.passAsDictator("ada@example.com", FakeDice.twoD6(2))

		assertThat(broke.turn!!.tradeAwaited().offer.playerEmail).isEqualTo("grace@example.com")
		assertThat(broke.players.first { it.email == "grace@example.com" }.tradeMax(broke.turn!!.tradeAwaited().offer, 21)).isZero()
		val game = broke.tradeNothing("grace@example.com")

		assertThat(game.turn!!.resolution!!.trades.single().units).isZero()
		assertThat(game.turn!!.resolution!!.isTradingOver()).isTrue()
		assertThat(game.log.drop(broke.log.size)).containsExactly(
			LogEntry(1, "Vega Lines could buy up to 21 u: bought nothing"),
			LogEntry(1, "No one can trade at Gamma Leporis POLY"),
			LogEntry(1, "POLY price at Gamma Leporis: 2 → 1 HT")
		)
	}

	@Test
	fun shouldRefuseToTradeNothingWhileTheTraderCanTradeAUnit() {
		// S04-5, R34: a trader who can trade buys or sells 1 to their max; trading nothing only acknowledges they can't.
		assertThatThrownBy { gracesTurnToTrade.tradeNothing("grace@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("You can buy up to 21 u: choose from 1 to 21.")
		assertThatThrownBy { gracesTurnToTrade.tradeNothing("kim@example.com") }
			.isInstanceOf(NotYourTurnException::class.java)
	}

	@Test
	fun shouldCheckTheDictatorsOfferLikeAnyOther() {
		// R03
		assertThatThrownBy { askingDictator.placeDictatorOffer("ada@example.com", OfferType.BUY, 21, dice()) }
			.isInstanceOf(InvalidOfferException::class.java)
			.hasMessage("Price must be a whole number from 1 to 20.")
		assertThatThrownBy { askingDictator.placeDictatorOffer("ada@example.com", OfferType.BUY, null, dice()) }
			.isInstanceOf(InvalidOfferException::class.java)
	}

	@Test
	fun shouldRefuseADecisionFromAnyoneButTheDictatorAsked() {
		// R38, R04
		listOf("grace@example.com", "kim@example.com").forEach { email ->
			assertThatThrownBy { askingDictator.passAsDictator(email, dice()) }
				.isInstanceOf(NotYourTurnException::class.java)
				.hasMessage("Waiting for Orion Freight (Dictator) to decide.")
			assertThatThrownBy { askingDictator.placeDictatorOffer(email, OfferType.BUY, 5, dice()) }.isInstanceOf(NotYourTurnException::class.java)
		}
		assertThatThrownBy { askingDictator.passAsDictator("someone@example.com", dice()) }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldRefuseADecisionWhenNoDictatorIsAsked() {
		// S02-4: a good with no Dictator goes straight on; a decided Dictator isn't asked again
		val decided = askingDictator.passAsDictator("ada@example.com", dice())

		listOf(trading.resolveNextGood("grace@example.com", dice()), decided).forEach { game ->
			assertThatThrownBy { game.passAsDictator("ada@example.com", dice()) }
				.isInstanceOf(InvalidGameOperationException::class.java)
				.hasMessage("No Dictator decision is pending.")
		}
	}

	@Test
	fun shouldGoStraightOnWhenTheGoodHasNoDictator() {
		// S02-4: Kim's Tau Ceti POLY offer is disclosed when that good resolves, and nobody is asked
		val order = listOf(MarketGood(TAU_CETI, Goods.POLY)) + withDictator.turn!!.tradingQueue.minus(MarketGood(TAU_CETI, Goods.POLY))

		val game = withDictator.reorderTradingQueue("grace@example.com", order).resolveNextGood("grace@example.com", dice())

		assertThat(game.turn!!.resolution!!.awaitingDictator()).isNull()
		assertThat(game.turn!!.resolvedGoods).containsExactly(MarketGood(TAU_CETI, Goods.POLY))
		assertThat(game.log.map { it.text }).contains("Offers for Tau Ceti POLY: Altair Trade Co. — sell at 9 HT")
	}
}
