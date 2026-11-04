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

class BiddingServiceTest {

	private val gameStore = FakeGameStore()
	private val service = BiddingService(gameStore, FakeDice(2, 1))
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val started = Game.lobby("game-1", 20, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))

	@Test
	fun shouldStoreTheDoneSignal() {
		gameStore.create(started)

		val game = service.signalDone("game-1", "joiner@example.com")

		assertThat(game.turn?.doneBidding).containsExactly("joiner@example.com")
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldMoveTheStoredGameToShipJumpsWhenTheLastPlayerSignalsDone() {
		gameStore.create(started)
		service.signalDone("game-1", "joiner@example.com")

		service.signalDone("game-1", "creator@example.com")

		assertThat(
			gameStore.find("game-1")?.turn
		).isEqualTo(
			Turn(1, TurnPhase.SHIP_JUMPS, emptyList(), emptyList(), emptyList(), listOf("creator@example.com", "joiner@example.com"), emptyList(), emptyList(), emptyList())
		)
	}

	@Test
	fun shouldCloseBiddingOnceEvenWhenTheStoreReplaysTheChange() {
		// A real store re-runs the change when another write lands first (GameStore.update); replaying it must not skip a phase.
		val replayingStore = ReplayingGameStore(gameStore)
		gameStore.create(started.signalDoneBidding("joiner@example.com", FakeDice(2, 1)))

		// The replay closes Bidding twice over the same stored game, so the dice roll twice; only one close is saved.
		BiddingService(replayingStore, FakeDice(2, 1, 2, 1)).signalDone("game-1", "creator@example.com")

		assertThat(
			gameStore.find("game-1")?.turn
		).isEqualTo(
			Turn(1, TurnPhase.SHIP_JUMPS, emptyList(), emptyList(), emptyList(), listOf("creator@example.com", "joiner@example.com"), emptyList(), emptyList(), emptyList())
		)
	}

	@Test
	fun shouldRefuseALateSignalAndLeaveTheGameAsItWas() {
		val closed = started.signalDoneBidding("joiner@example.com", FakeDice(2, 1)).signalDoneBidding("creator@example.com", FakeDice(2, 1))
		gameStore.create(closed)

		assertThatThrownBy { service.signalDone("game-1", "joiner@example.com") }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Bidding has closed")
		assertThat(gameStore.find("game-1")).isEqualTo(closed)
	}

	@Test
	fun shouldRejectAnUnknownGame() {
		assertThatThrownBy { service.signalDone("missing", "joiner@example.com") }.isInstanceOf(GameNotFoundException::class.java)
	}

	@Test
	fun shouldRejectSomeoneWhoIsNotInTheGame() {
		gameStore.create(started)

		assertThatThrownBy { service.signalDone("game-1", "stranger@example.com") }.isInstanceOf(NotAGameMemberException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(started)
	}

	@Test
	fun shouldStoreAPlacedOffer() {
		gameStore.create(started)

		val game = service.placeOffer("game-1", "joiner@example.com", TAU_CETI, Goods.POLY, OfferType.BUY, 8)

		assertThat(game.turn?.offers).containsExactly(Offer("joiner@example.com", TAU_CETI, Goods.POLY, OfferType.BUY, 8))
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldRefuseAnOfferThatArrivesAfterTheLastDoneAndLeaveTheGameAsItWas() {
		val closed = started.signalDoneBidding("joiner@example.com", FakeDice(2, 1)).signalDoneBidding("creator@example.com", FakeDice(2, 1))
		gameStore.create(closed)

		assertThatThrownBy { service.placeOffer("game-1", "joiner@example.com", TAU_CETI, Goods.POLY, OfferType.BUY, 8) }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Bidding has closed")
		assertThat(gameStore.find("game-1")).isEqualTo(closed)
	}

	@Test
	fun shouldPlaceAnOfferOnceEvenWhenTheStoreReplaysTheChange() {
		gameStore.create(started)

		BiddingService(
			ReplayingGameStore(gameStore),
			FakeDice()
		).placeOffer("game-1", "joiner@example.com", TAU_CETI, Goods.POLY, OfferType.BUY, 8)

		assertThat(gameStore.find("game-1")?.turn?.offers).hasSize(1)
	}

	@Test
	fun shouldRefuseAnOfferForAGameThatDoesNotExist() {
		assertThatThrownBy { service.placeOffer("missing", "joiner@example.com", TAU_CETI, Goods.POLY, OfferType.BUY, 8) }
			.isInstanceOf(GameNotFoundException::class.java)
	}

	@Test
	fun shouldStoreAnInitiativeBid() {
		gameStore.create(started)

		val game = service.placeInitiativeBid("game-1", "joiner@example.com", 40)

		assertThat(game.turn?.bids).containsExactly(InitiativeBid("joiner@example.com", 40))
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldKeepOneBidEvenWhenTheStoreReplaysTheChange() {
		gameStore.create(started)

		BiddingService(ReplayingGameStore(gameStore), FakeDice()).placeInitiativeBid("game-1", "joiner@example.com", 40)

		assertThat(gameStore.find("game-1")?.turn?.bids).containsExactly(InitiativeBid("joiner@example.com", 40))
	}

	@Test
	fun shouldChargeTheBidOnceWhenTheLastDoneIsReplayed() {
		gameStore.create(started.placeInitiativeBid("joiner@example.com", 40).signalDoneBidding("joiner@example.com", FakeDice()))

		BiddingService(ReplayingGameStore(gameStore), FakeDice(2, 1, 2, 1)).signalDone("game-1", "creator@example.com")

		assertThat(gameStore.find("game-1")?.players?.map { it.money }).containsExactly(300, 260)
	}

	@Test
	fun shouldRefuseABidForAGameThatDoesNotExist() {
		assertThatThrownBy { service.placeInitiativeBid("missing", "joiner@example.com", 40) }.isInstanceOf(GameNotFoundException::class.java)
	}

	/** Runs every change twice against the same stored state, the way an optimistic retry re-runs it. */
	private class ReplayingGameStore(private val delegate: FakeGameStore) : GameStore by delegate {

		override fun update(id: String, mutate: (Game) -> Game): Game {
			mutate(delegate.games.getValue(id))
			return delegate.update(id, mutate)
		}
	}
}
