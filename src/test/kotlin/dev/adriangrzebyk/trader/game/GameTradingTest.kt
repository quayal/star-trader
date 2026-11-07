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
			LogEntry(1, "No one can trade at Gamma Leporis POLY")
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
		assertThat(resolution.isFinished()).isTrue()
		assertThat(game.turn!!.resolvedGoods).containsExactly(MarketGood(GAMMA_LEPORIS, Goods.POLY))
		assertThat(game.log).contains(LogEntry(1, "Orion Freight (Dictator) offers to sell at 9 HT"))
		assertThat(game.turn!!.offersOf("ada@example.com")).isEmpty()
	}

	@Test
	fun shouldLetTheDictatorPass() {
		// S02-2
		val game = askingDictator.passAsDictator("ada@example.com", dice())

		assertThat(game.turn!!.resolution!!.offers).hasSize(2)
		assertThat(game.turn!!.resolvedGoods).containsExactly(MarketGood(GAMMA_LEPORIS, Goods.POLY))
		assertThat(game.log).contains(LogEntry(1, "Orion Freight (Dictator) passed"))
		assertThat(game.resolveNextGood("grace@example.com", dice()).turn!!.resolvedGoods).hasSize(2)
	}

	@Test
	fun shouldStartTheCounterAfterTheDictatorDecidesAndLogEachTrade() {
		// S03-1, S03-2, S03-6. Gamma Leporis POLY: S/D +1, 2 HT. Vega's buy at 11 and Altair's sell at 10 saturate (R15):
		// targets -18 for both. Roll 2: counter 3, so Vega buys 3 - (-18) = 21, and then Altair's sell allows nothing.
		assertThat(askingDictator.turn!!.resolution!!.startingCounter).isNull()

		val game = askingDictator.passAsDictator("ada@example.com", FakeDice.twoD6(2))

		assertThat(game.turn!!.resolution!!.trades).containsExactly(
			Trade(Offer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11), 21)
		)
		assertThat(game.log.drop(askingDictator.log.size)).containsExactly(
			LogEntry(1, "Orion Freight (Dictator) passed"),
			LogEntry(1, "Gamma Leporis POLY: market +3 → buyers first"),
			LogEntry(1, "Vega Lines bought 21 u at 11 HT each"),
			LogEntry(1, "No one else can trade at Gamma Leporis POLY")
		)
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
