package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameTurnEngineTest {

	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val lobby = Game.lobby("game-1", 2, creator).addPlayer(joiner)
	private val started = lobby.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))

	@Test
	fun shouldStartInTurnOneBiddingWithNobodyDone() {
		assertThat(started.turn).isEqualTo(Turn(1, TurnPhase.BIDDING, emptyList()))
	}

	@Test
	fun shouldRunBiddingThenShipJumpsThenTradingThenTheNextTurnsBidding() {
		val phases = generateSequence(started) { it.advancePhase() }.take(4).map { it.turn }.toList()

		assertThat(phases).containsExactly(
			Turn(1, TurnPhase.BIDDING, emptyList()),
			Turn(1, TurnPhase.SHIP_JUMPS, emptyList()),
			Turn(1, TurnPhase.TRADING, emptyList()),
			Turn(2, TurnPhase.BIDDING, emptyList())
		)
	}

	@Test
	fun shouldFinishTheGameAfterTheLastTurnsTrading() {
		val lastTrading = (1..5).fold(started) { game, _ -> game.advancePhase() }
		assertThat(lastTrading.turn).isEqualTo(Turn(2, TurnPhase.TRADING, emptyList()))

		val finished = lastTrading.advancePhase()

		assertThat(finished.status).isEqualTo(GameStatus.FINISHED)
		assertThat(finished.turn).isEqualTo(Turn(2, TurnPhase.TRADING, emptyList()))
	}

	@Test
	fun shouldNotAdvanceAGameThatHasNotStarted() {
		assertThatThrownBy { lobby.advancePhase() }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game hasn't started")
	}

	@Test
	fun shouldNotAdvanceAFinishedGame() {
		val finished = (1..6).fold(started) { game, _ -> game.advancePhase() }

		assertThatThrownBy { finished.advancePhase() }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game is over")
	}

	@Test
	fun shouldRecordAPlayerAsDoneWhileOthersStillBid() {
		val game = started.signalDoneBidding("joiner@example.com")

		assertThat(game.turn).isEqualTo(Turn(1, TurnPhase.BIDDING, listOf("joiner@example.com")))
	}

	@Test
	fun shouldCloseBiddingTheMomentTheLastPlayerSignalsDone() {
		val game = started.signalDoneBidding("joiner@example.com").signalDoneBidding("creator@example.com")

		assertThat(game.turn).isEqualTo(Turn(1, TurnPhase.SHIP_JUMPS, emptyList()))
	}

	@Test
	fun shouldIgnoreASecondDoneFromTheSamePlayer() {
		val once = started.signalDoneBidding("joiner@example.com")

		assertThat(once.signalDoneBidding("joiner@example.com")).isEqualTo(once)
	}

	@Test
	fun shouldRejectDoneOnceBiddingHasClosed() {
		val closed = started.signalDoneBidding("joiner@example.com").signalDoneBidding("creator@example.com")

		assertThatThrownBy { closed.signalDoneBidding("joiner@example.com") }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Bidding has closed")
	}

	@Test
	fun shouldRejectDoneFromSomeoneWhoIsNotInTheGame() {
		assertThatThrownBy { started.signalDoneBidding("stranger@example.com") }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldRejectDoneBeforeTheGameHasStarted() {
		assertThatThrownBy { lobby.signalDoneBidding("joiner@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game hasn't started")
	}
}
