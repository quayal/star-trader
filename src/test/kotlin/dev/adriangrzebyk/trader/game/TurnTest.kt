package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TurnTest {

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

		val turn = trading.resolveNext().resolveNext()

		assertThat(turn.resolvedGoods).containsExactlyElementsOf(trading.tradingQueue.take(2))
		assertThat(turn.tradingQueue).containsExactlyElementsOf(trading.tradingQueue.drop(2))
		assertThat(turn.nextGood()).isEqualTo(trading.tradingQueue[2])
	}

	@Test
	fun shouldRefuseToResolveOnceEveryGoodHasResolved() {
		val trading = shipJumps.toTrading()
		val done = trading.tradingQueue.fold(trading) { turn, _ -> turn.resolveNext() }

		assertThatThrownBy { done.resolveNext() }.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Every good has been resolved.")
	}

	@Test
	fun shouldResolveTheGoodsInTheNewOrderAfterAReorder() {
		// R25, S01-3
		val trading = shipJumps.toTrading()
		val reversed = trading.tradingQueue.reversed()

		val turn = trading.reorderQueue(reversed).resolveNext()

		assertThat(turn.resolvedGoods).containsExactly(reversed.first())
		assertThat(turn.tradingQueue).containsExactlyElementsOf(reversed.drop(1))
	}

	@Test
	fun shouldRefuseAnOrderThatDoesNotHoldExactlyTheRemainingGoods() {
		// A04: an order sent before a good resolved is stale
		val trading = shipJumps.toTrading()
		val resolved = trading.resolveNext()
		val duplicated = resolved.tradingQueue.drop(1) + resolved.tradingQueue[1]

		listOf(trading.tradingQueue, resolved.tradingQueue.drop(1), duplicated).forEach { order ->
			assertThatThrownBy { resolved.reorderQueue(order) }.isInstanceOf(InvalidGameOperationException::class.java)
				.hasMessage("The remaining goods have changed. Try again.")
		}
	}

	@Test
	fun shouldRefuseTradingActionsBeforeTrading() {
		assertThatThrownBy { shipJumps.resolveNext() }.hasMessage("Trading hasn't started.")
		assertThatThrownBy { shipJumps.reorderQueue(emptyList()) }.hasMessage("Trading hasn't started.")
	}

	@Test
	fun shouldLetTheFirstInInitiativeResolve() {
		assertThat(shipJumps.toTrading().firstInInitiative()).isEqualTo("a")
		assertThatThrownBy { Turn.bidding(1).toTrading().firstInInitiative() }.hasMessage("Nobody is first in initiative this turn.")
	}
}
