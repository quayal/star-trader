package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TurnTest {

	/** Enough 2d6 rolls for every good on the board to resolve (R36), all 7. */
	private fun dice() = FakeDice.twoD6(*IntArray(MarketGood.everyOnBoard().size) { 7 })

	private val shipJumps = Turn.bidding(1).copy(phase = TurnPhase.SHIP_JUMPS, initiative = listOf("a", "b", "c"))

	@Test
	fun shouldRefuseASecondOfferOnTheSameGoodAndSystem() {
		// R33
		val turn = Turn.bidding(1).placeOffer(Offer("ada@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.BUY, 8))

		assertThatThrownBy { turn.placeOffer(Offer("ada@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.SELL, 9)) }
			.isInstanceOf(InvalidOfferException::class.java)
			.hasMessage("You already have an offer on POLY at Tau Ceti this turn.")
	}

	@Test
	fun shouldRefuseABidOnceBiddingHasClosed() {
		assertThatThrownBy { shipJumps.placeBid(InitiativeBid("ada@example.com", 10)) }.isInstanceOf(PhaseClosedException::class.java)
	}

	@Test
	fun shouldReplaceAnEarlierBid() {
		val turn = Turn.bidding(1).placeBid(InitiativeBid("ada@example.com", 10)).placeBid(InitiativeBid("ada@example.com", 25))

		assertThat(turn.bids).containsExactly(InitiativeBid("ada@example.com", 25))
	}

	@Test
	fun shouldGiveTheLastPlaceToTheLastPlayerWithoutAsking() {
		// S01-6
		val turn = shipJumps.pickPlace("a", 2).pickPlace("b", 3)

		assertThat(turn.turnOrder()).containsExactly("c", "a", "b")
		assertThat(turn.picker()).isNull()
	}

	@Test
	fun shouldRefuseAPlaceThatIsTaken() {
		assertThatThrownBy { shipJumps.pickPlace("a", 2).pickPlace("b", 2) }.isInstanceOf(InvalidPlaceException::class.java)
	}

	@Test
	fun shouldMoveToTradingWhenTheLastJumpSlotEnds() {
		// S02-8
		val ordered = shipJumps.pickPlace("a", 1).pickPlace("b", 2)

		val turn = ordered.endJumpSlot("a").endJumpSlot("b").endJumpSlot("c")

		assertThat(turn.phase).isEqualTo(TurnPhase.TRADING)
	}

	@Test
	fun shouldWaitForTheJumpedShipToLand() {
		val ship = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)
		val turn = shipJumps.recordJump(ship)

		assertThat(turn.hasJumped(ship)).isTrue()
		assertThatThrownBy { turn.refuseWhileAShipWaitsToLand() }.hasMessage("Choose where Wayfarer ends up first.")
		assertThatThrownBy { turn.landed().shipWaitingToLand() }.hasMessage("No ship is waiting to land.")
	}

	@Test
	fun shouldQueueEveryGoodAtEverySystemWhenTradingStarts() {
		// R36, S01-2: by system in map order, then good
		val turn = shipJumps.toTrading()

		assertThat(turn.tradingQueue).hasSize(18)
		assertThat(turn.tradingQueue.take(4)).containsExactly(
			MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY),
			MarketGood(StarSystem.TAU_CETI, Goods.ISO),
			MarketGood(StarSystem.TAU_CETI, Goods.POLY),
			MarketGood(StarSystem.EPSILON_ERIDANI, Goods.CHIPS)
		)
		assertThat(turn.tradingQueue.last()).isEqualTo(MarketGood(StarSystem.SIGMA_DRACONIS, Goods.POLY))
		assertThat(turn.resolvedGoods).isEmpty()
		assertThat(Turn.bidding(1).tradingQueue).isEmpty()
	}

	@Test
	fun shouldResolveTheGoodsInQueueOrder() {
		// R25
		val trading = shipJumps.toTrading()

		val turn = trading.resolveNext(null, dice()).resolveNext(null, dice())

		assertThat(turn.resolvedGoods).containsExactlyElementsOf(trading.tradingQueue.take(2))
		assertThat(turn.tradingQueue).containsExactlyElementsOf(trading.tradingQueue.drop(2))
		assertThat(turn.nextGood()).isEqualTo(trading.tradingQueue[2])
	}

	@Test
	fun shouldRefuseToResolveOnceEveryGoodHasResolved() {
		val trading = shipJumps.toTrading()
		val done = trading.tradingQueue.fold(trading) { turn, _ -> turn.resolveNext(null, dice()) }

		assertThatThrownBy { done.resolveNext(null, dice()) }.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Every good has been resolved.")
	}

	@Test
	fun shouldResolveTheGoodsInTheNewOrderAfterAReorder() {
		// R25, S01-3
		val trading = shipJumps.toTrading()
		val reversed = trading.tradingQueue.reversed()

		val turn = trading.reorderQueue(reversed).resolveNext(null, dice())

		assertThat(turn.resolvedGoods).containsExactly(reversed.first())
		assertThat(turn.tradingQueue).containsExactlyElementsOf(reversed.drop(1))
	}

	@Test
	fun shouldRefuseAnOrderThatDoesNotHoldExactlyTheRemainingGoods() {
		// A04: an order sent before a good resolved is stale
		val trading = shipJumps.toTrading()
		val resolved = trading.resolveNext(null, dice())
		val duplicated = resolved.tradingQueue.drop(1) + resolved.tradingQueue[1]

		listOf(trading.tradingQueue, resolved.tradingQueue.drop(1), duplicated).forEach { order ->
			assertThatThrownBy { resolved.reorderQueue(order) }.isInstanceOf(InvalidGameOperationException::class.java)
				.hasMessage("The remaining goods have changed. Try again.")
		}
	}

	@Test
	fun shouldRefuseTradingActionsBeforeTrading() {
		assertThatThrownBy { shipJumps.resolveNext(null, dice()) }.hasMessage("Trading hasn't started.")
		assertThatThrownBy { shipJumps.reorderQueue(emptyList()) }.hasMessage("Trading hasn't started.")
	}

	@Test
	fun shouldLetTheFirstInInitiativeResolve() {
		assertThat(shipJumps.toTrading().firstInInitiative()).isEqualTo("a")
		assertThatThrownBy { Turn.bidding(1).toTrading().firstInInitiative() }.hasMessage("Nobody is first in initiative this turn.")
	}

	@Test
	fun shouldDiscloseTheOffersForTheGoodResolvingOnly() {
		// E05-S02-1: offers for that good at that system, in the order they were placed
		val vega = Offer("vega@example.com", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11)
		val orion = Offer("orion@example.com", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.SELL, 10)
		val elsewhere = Offer("vega@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.SELL, 9)
		val trading = shipJumps.copy(offers = listOf(vega, elsewhere, orion)).toTrading()

		val turn = trading.resolveNext(null, dice())

		assertThat(turn.resolution!!.good).isEqualTo(MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY))
		assertThat(turn.resolution!!.offers).containsExactly(vega, orion)
		assertThat(turn.resolution!!.initiative).isEqualTo(trading.initiative)
	}

	@Test
	fun shouldWaitForTheDictatorBeforeTheGoodCountsAsResolved() {
		// S02-2, S01-4: the next good can't start while the Dictator decides; the queue can still be reordered
		val trading = shipJumps.toTrading()

		val turn = trading.resolveNext("c", dice())

		assertThat(turn.dictatorToDecide()).isEqualTo("c")
		assertThat(turn.resolvedGoods).isEmpty()
		assertThat(turn.tradingQueue).containsExactlyElementsOf(trading.tradingQueue.drop(1))
		assertThatThrownBy { turn.resolveNext(null, dice()) }.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Gamma Leporis POLY hasn't finished resolving.")
		assertThat(turn.reorderQueue(turn.tradingQueue.reversed()).tradingQueue).containsExactlyElementsOf(turn.tradingQueue.reversed())
	}

	@Test
	fun shouldFinishTheResolutionOnceTheDictatorPlacesAnOfferOrPasses() {
		// S02-2, S02-3
		val waiting = shipJumps.toTrading().resolveNext("c", dice())
		val offer = Offer("c", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.SELL, 10)

		val offered = waiting.placeDictatorOffer(offer, dice())
		val passed = waiting.dictatorPassed(dice())

		assertThat(offered.resolution!!.offers).containsExactly(offer)
		assertThat(offered.resolution!!.dictatorOffer()).isEqualTo(offer)
		assertThat(offered.resolvedGoods).containsExactly(MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY))
		assertThat(passed.resolution!!.offers).isEmpty()
		assertThat(passed.resolution!!.dictatorOffer()).isNull()
		assertThat(passed.resolvedGoods).containsExactly(MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY))
		assertThat(passed.resolveNext(null, dice()).resolvedGoods).hasSize(2)
	}

	@Test
	fun shouldRefuseADictatorDecisionWhenNobodyIsAsked() {
		// S02-4: no Dictator, no step; and a Dictator decides once
		val noDictator = shipJumps.toTrading().resolveNext(null, dice())
		val decided = shipJumps.toTrading().resolveNext("c", dice()).dictatorPassed(dice())

		listOf(shipJumps.toTrading(), noDictator, decided).forEach { turn ->
			assertThatThrownBy { turn.dictatorToDecide() }.hasMessage("No Dictator decision is pending.")
			assertThatThrownBy { turn.dictatorPassed(dice()) }.hasMessage("No Dictator decision is pending.")
		}
		assertThatThrownBy { shipJumps.dictatorToDecide() }.hasMessage("Trading hasn't started.")
	}

	@Test
	fun shouldStartTradingWithNoResolution() {
		val resolved = shipJumps.toTrading().resolveNext(null, dice())

		assertThat(resolved.copy(phase = TurnPhase.SHIP_JUMPS).toTrading().resolution).isNull()
	}

	@Test
	fun shouldStartTheCounterOnlyOnceTheDictatorHasDecided() {
		// R38, S03-1: the roll doesn't exist while the Dictator is asked. Gamma Leporis POLY: S/D +1.
		val waiting = shipJumps.toTrading().resolveNext("c", FakeDice.twoD6())

		assertThat(waiting.resolution!!.startingCounter).isNull()
		assertThat(waiting.dictatorPassed(FakeDice.twoD6(4)).resolution!!.startingCounter).isEqualTo(5)
		assertThat(shipJumps.toTrading().resolveNext(null, FakeDice.twoD6(2)).resolution!!.startingCounter).isEqualTo(3)
	}

	@Test
	fun shouldBreakTiesByTurnInitiativeOrder() {
		// R34, R24: two buys at the same price; "b" is ahead of "c" in initiative, though "c" placed first. Counter 1 + 6 = 7:
		// b may buy 8 first.
		val c = Offer("c", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 2)
		val b = Offer("b", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 2)

		val turn = shipJumps.copy(offers = listOf(c, b)).toTrading().resolveNext(null, FakeDice.twoD6(6))

		assertThat(turn.tradeAwaited()).isEqualTo(Trade(b, 8))
	}

	@Test
	fun shouldWaitForEachTraderBeforeTheGoodCountsAsResolved() {
		// E05-S04, S01-4: b buys 3 of 8 (counter 4); c may then buy 4 - (-1) = 5, and once c has, the good is resolved.
		val c = Offer("c", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 2)
		val b = Offer("b", StarSystem.GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 2)
		val started = shipJumps.copy(offers = listOf(c, b)).toTrading().resolveNext(null, FakeDice.twoD6(6))

		val bBought = started.recordTrade(Trade(b, 3))

		assertThat(bBought.resolvedGoods).isEmpty()
		assertThatThrownBy { bBought.resolveNext(null, dice()) }.hasMessage("Gamma Leporis POLY hasn't finished resolving.")
		assertThat(bBought.tradeAwaited()).isEqualTo(Trade(c, 5))
		val cBought = bBought.recordTrade(Trade(c, 5))
		assertThat(cBought.resolvedGoods).containsExactly(MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY))
		assertThatThrownBy { cBought.tradeAwaited() }.hasMessage("No trade is waiting for a decision.")
	}
}
