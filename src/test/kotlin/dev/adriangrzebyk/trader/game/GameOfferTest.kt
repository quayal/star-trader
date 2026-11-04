package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods.CHIPS
import dev.adriangrzebyk.trader.trading.Goods.FOOD
import dev.adriangrzebyk.trader.trading.Goods.ISO
import dev.adriangrzebyk.trader.trading.Goods.POLY
import dev.adriangrzebyk.trader.trading.OfferType.BUY
import dev.adriangrzebyk.trader.trading.OfferType.SELL
import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameOfferTest {

	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val lobby = Game.lobby("game-1", 2, creator).addPlayer(joiner)
	private val started = lobby.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))

	@Test
	fun shouldPlaceAnOfferDuringBidding() {
		val game = started.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)

		assertThat(game.turn?.offers).containsExactly(Offer("joiner@example.com", TAU_CETI, POLY, BUY, 8))
	}

	@Test
	fun shouldStillPlaceAnOfferAfterSignallingDoneWhileOthersBid() {
		// S04-2, R17: done is a readiness flag, not a commit.
		val game = started.signalDoneBidding("joiner@example.com", FakeDice(2, 1)).placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)

		assertThat(game.turn).isEqualTo(
			Turn(
				1,
				TurnPhase.BIDDING,
				listOf("joiner@example.com"),
				listOf(Offer("joiner@example.com", TAU_CETI, POLY, BUY, 8)),
				emptyList(),
				emptyList(),
				emptyList(),
				emptyList(),
				emptyList()
			)
		)
	}

	@Test
	fun shouldKeepOffersInTheOrderTheyWerePlaced() {
		val game = started
			.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)
			.placeOffer("creator@example.com", TAU_CETI, POLY, SELL, 9)
			.placeOffer("joiner@example.com", BETA_HYDRI, CHIPS, SELL, 20)

		assertThat(game.turn?.offers).containsExactly(
			Offer("joiner@example.com", TAU_CETI, POLY, BUY, 8),
			Offer("creator@example.com", TAU_CETI, POLY, SELL, 9),
			Offer("joiner@example.com", BETA_HYDRI, CHIPS, SELL, 20)
		)
	}

	@Test
	fun shouldGiveEachPlayerOnlyTheirOwnOffers() {
		val game = started
			.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)
			.placeOffer("creator@example.com", TAU_CETI, ISO, SELL, 9)

		assertThat(game.turn?.offersOf("joiner@example.com")).containsExactly(Offer("joiner@example.com", TAU_CETI, POLY, BUY, 8))
		assertThat(game.turn?.offersOf("creator@example.com")).containsExactly(Offer("creator@example.com", TAU_CETI, ISO, SELL, 9))
	}

	@Test
	fun shouldAcceptTheLowestAndHighestPrice() {
		val game = started
			.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 1)
			.placeOffer("joiner@example.com", TAU_CETI, ISO, SELL, 20)

		assertThat(game.turn?.offers?.map { it.price }).containsExactly(1, 20)
	}

	@Test
	fun shouldRefuseAPriceOutsideOneToTwenty() {
		listOf(0, 21, -3, null).forEach { price ->
			assertThatThrownBy { started.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, price) }
				.isInstanceOf(InvalidOfferException::class.java)
				.hasMessage("Price must be a whole number from 1 to 20.")
		}
	}

	@Test
	fun shouldRefuseASecondOfferOnTheSameGoodAtTheSameSystem() {
		val game = started.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)

		assertThatThrownBy { game.placeOffer("joiner@example.com", TAU_CETI, POLY, SELL, 12) }
			.isInstanceOf(InvalidOfferException::class.java)
			.hasMessage("You already have an offer on POLY at Tau Ceti this turn.")
	}

	@Test
	fun shouldLetAnotherPlayerOfferOnTheSameGoodAndSystem() {
		val game = started.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)
			.placeOffer("creator@example.com", TAU_CETI, POLY, BUY, 8)

		assertThat(game.turn?.offers).hasSize(2)
	}

	@Test
	fun shouldLetAPlayerOfferTheSameGoodAtAnotherSystem() {
		val game = started.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)
			.placeOffer("joiner@example.com", BETA_HYDRI, POLY, BUY, 8)

		assertThat(game.turn?.offers).hasSize(2)
	}

	@Test
	fun shouldRefuseAGoodTheSystemDoesNotTrade() {
		assertThatThrownBy { started.placeOffer("joiner@example.com", GAMMA_LEPORIS, FOOD, BUY, 8) }
			.isInstanceOf(InvalidOfferException::class.java)
			.hasMessage("FOOD isn't traded at Gamma Leporis.")
	}

	@Test
	fun shouldRefuseTheDictatorsOfferForTheirGoodAtThatSystem() {
		val withDictator = withPosition("joiner@example.com", MarketPosition(TAU_CETI, POLY, MarketTier.DICTATOR))

		assertThatThrownBy { withDictator.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8) }
			.isInstanceOf(InvalidOfferException::class.java)
			.hasMessage("You're Dictator of POLY here — you place that offer when it resolves in Trading.")
	}

	@Test
	fun shouldLetADictatorOfferOtherGoodsAndTheSameGoodElsewhere() {
		val withDictator = withPosition("joiner@example.com", MarketPosition(TAU_CETI, POLY, MarketTier.DICTATOR))

		val game = withDictator
			.placeOffer("joiner@example.com", TAU_CETI, ISO, BUY, 8)
			.placeOffer("joiner@example.com", BETA_HYDRI, POLY, BUY, 8)

		assertThat(game.turn?.offers).hasSize(2)
	}

	@Test
	fun shouldLetAWholesalerOfferTheirGood() {
		val withWholesaler = withPosition("joiner@example.com", MarketPosition(TAU_CETI, POLY, MarketTier.WHOLESALER))

		assertThat(withWholesaler.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8).turn?.offers).hasSize(1)
	}

	@Test
	fun shouldRefuseAnOfferOnceBiddingHasClosed() {
		val closed = started.advancePhase(FakeDice(2, 1))

		assertThatThrownBy { closed.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8) }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Bidding has closed")
	}

	@Test
	fun shouldRefuseAnOfferFromSomeoneOutsideTheGame() {
		assertThatThrownBy { started.placeOffer("stranger@example.com", TAU_CETI, POLY, BUY, 8) }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldRefuseAnOfferBeforeTheGameStarts() {
		assertThatThrownBy { lobby.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game hasn't started")
	}

	@Test
	fun shouldKeepOffersFromBiddingThroughTrading() {
		val offer = Offer("joiner@example.com", TAU_CETI, POLY, BUY, 8)
		val bidding = started.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)

		val trading = bidding.signalDoneBidding(
			"joiner@example.com",
			FakeDice(2, 1)
		).signalDoneBidding("creator@example.com", FakeDice(2, 1)).advancePhase(FakeDice(2, 1))

		assertThat(
			trading.turn
		).isEqualTo(
			Turn(1, TurnPhase.TRADING, emptyList(), listOf(offer), emptyList(), listOf("creator@example.com", "joiner@example.com"), emptyList(), emptyList(), emptyList())
		)
	}

	@Test
	fun shouldStartTheNextTurnWithNoOffers() {
		val bidding = started.placeOffer("joiner@example.com", TAU_CETI, POLY, BUY, 8)

		val nextTurn = (1..3).fold(bidding) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

		assertThat(nextTurn.turn).isEqualTo(Turn.bidding(2))
	}

	private fun withPosition(email: String, position: MarketPosition): Game =
		started.copy(players = started.players.map { if (it.email == email) it.copy(marketPositions = listOf(position)) else it })
}
