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
}
