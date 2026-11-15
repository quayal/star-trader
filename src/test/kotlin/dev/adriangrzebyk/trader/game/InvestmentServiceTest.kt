package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E06-S01-2: Investment closes the moment the last player signals done, exactly once (A04).
class InvestmentServiceTest {

	private val gameStore = FakeGameStore()
	private val service = InvestmentService(gameStore)
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val started = Game.lobby("game-1", 20, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))

	/** [started] taken through Bidding, Ship-jumps and Trading to the start of Investment: advancing a phase needs no players' acts. */
	private val investing = (1..3).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

	@Test
	fun shouldStoreTheDoneSignal() {
		gameStore.create(investing)

		val game = service.signalDone("game-1", "joiner@example.com")

		assertThat(game.turn?.doneInvesting).containsExactly("joiner@example.com")
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldStartTheNextTurnInTheStoreWhenTheLastPlayerSignalsDone() {
		gameStore.create(investing)
		service.signalDone("game-1", "joiner@example.com")

		service.signalDone("game-1", "creator@example.com")

		assertThat(gameStore.find("game-1")?.turn).isEqualTo(Turn.bidding(2))
	}

	@Test
	fun shouldEndTheTurnOnceEvenWhenTheStoreReplaysTheChange() {
		// A real store re-runs the change when another write lands first (GameStore.update); the turn must end only once.
		gameStore.create(investing.signalDoneInvesting("joiner@example.com"))

		InvestmentService(ReplayingGameStore(gameStore)).signalDone("game-1", "creator@example.com")

		val stored = gameStore.find("game-1")!!
		assertThat(stored.turn).isEqualTo(Turn.bidding(2))
		assertThat(stored.log.count { it.text.startsWith("Turn 2 of 20 starts") }).isEqualTo(1)
	}

	@Test
	fun shouldRefuseALateSignalAndLeaveTheGameAsItWas() {
		val closed = investing.signalDoneInvesting("joiner@example.com").signalDoneInvesting("creator@example.com")
		gameStore.create(closed)

		assertThatThrownBy { service.signalDone("game-1", "joiner@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
		assertThat(gameStore.find("game-1")).isEqualTo(closed)
	}

	@Test
	fun shouldRefuseASignalOutsideInvestment() {
		gameStore.create(started)

		assertThatThrownBy { service.signalDone("game-1", "joiner@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldRefuseASignalFromSomeoneWhoIsNotInTheGame() {
		gameStore.create(investing)

		assertThatThrownBy { service.signalDone("game-1", "stranger@example.com") }.isInstanceOf(NotAGameMemberException::class.java)
	}

	/** Runs every change twice against the same stored state, the way an optimistic retry re-runs it. */
	private class ReplayingGameStore(private val delegate: FakeGameStore) : GameStore by delegate {

		override fun update(id: String, mutate: (Game) -> Game): Game {
			mutate(delegate.games.getValue(id))
			return delegate.update(id, mutate)
		}
	}
}
