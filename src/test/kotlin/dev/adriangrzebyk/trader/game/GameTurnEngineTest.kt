package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameTurnEngineTest {

	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val lobby = Game.lobby("game-1", 2, creator).addPlayer(joiner)
	private val started = lobby.start(
		"creator@example.com",
		FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS),
		START_DICE
	).let {
		ConnectionsPlaced(it).byEveryone()
	}

	@Test
	fun shouldStartInTurnOneBiddingWithNobodyDone() {
		assertThat(started.turn).isEqualTo(Turn.bidding(1))
	}

	@Test
	fun shouldRunBiddingThenShipJumpsThenTradingThenOpportunityThenInvestmentThenTheNextTurnsBidding() {
		val phases = generateSequence(started) { it.advancePhase(FakeDice(2, 1)) }.take(6).map { it.turn }.toList()

		assertThat(phases).containsExactly(
			Turn.bidding(1),
			Turn.bidding(1).copy(
				phase = TurnPhase.SHIP_JUMPS,
				initiative = listOf("creator@example.com", "joiner@example.com"),
				initiativeRolls = listOf(InitiativeRoll("creator@example.com", 0, 0, listOf(2)), InitiativeRoll("joiner@example.com", 0, 0, listOf(1)))
			),
			Turn.bidding(
				1
			).copy(
				phase = TurnPhase.TRADING,
				tradingQueue = MarketGood.everyOnBoard(),
				initiative = listOf("creator@example.com", "joiner@example.com"),
				initiativeRolls = listOf(InitiativeRoll("creator@example.com", 0, 0, listOf(2)), InitiativeRoll("joiner@example.com", 0, 0, listOf(1)))
			),
			Turn.bidding(1).copy(
				phase = TurnPhase.OPPORTUNITY,
				tradingQueue = MarketGood.everyOnBoard(),
				initiative = listOf("creator@example.com", "joiner@example.com"),
				initiativeRolls = listOf(InitiativeRoll("creator@example.com", 0, 0, listOf(2)), InitiativeRoll("joiner@example.com", 0, 0, listOf(1)))
			),
			Turn.bidding(
				1
			).copy(
				phase = TurnPhase.INVESTMENT,
				tradingQueue = MarketGood.everyOnBoard(),
				initiative = listOf("creator@example.com", "joiner@example.com"),
				initiativeRolls = listOf(InitiativeRoll("creator@example.com", 0, 0, listOf(2)), InitiativeRoll("joiner@example.com", 0, 0, listOf(1)))
			),
			Turn.bidding(2)
		)
	}

	@Test
	fun shouldFinishTheGameAfterTheLastTurnsInvestment() {
		val lastTrading = (1..7).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }
		assertThat(
			lastTrading.turn
		).isEqualTo(
			Turn.bidding(
				2
			).copy(
				phase = TurnPhase.TRADING,
				tradingQueue = MarketGood.everyOnBoard(),
				initiative = listOf("creator@example.com", "joiner@example.com"),
				initiativeRolls = listOf(InitiativeRoll("creator@example.com", 0, 0, listOf(2)), InitiativeRoll("joiner@example.com", 0, 0, listOf(1)))
			)
		)

		val lastInvestment = lastTrading.advancePhase(FakeDice(2, 1)).advancePhase(FakeDice(2, 1))
		assertThat(lastInvestment.status).isEqualTo(GameStatus.RUNNING)
		assertThat(lastInvestment.turn!!.phase).isEqualTo(TurnPhase.INVESTMENT)

		val finished = lastInvestment.advancePhase(FakeDice(2, 1))

		assertThat(finished.status).isEqualTo(GameStatus.FINISHED)
		assertThat(
			finished.turn
		).isEqualTo(
			Turn.bidding(
				2
			).copy(
				phase = TurnPhase.INVESTMENT,
				tradingQueue = MarketGood.everyOnBoard(),
				initiative = listOf("creator@example.com", "joiner@example.com"),
				initiativeRolls = listOf(InitiativeRoll("creator@example.com", 0, 0, listOf(2)), InitiativeRoll("joiner@example.com", 0, 0, listOf(1)))
			)
		)
	}

	@Test
	fun shouldNotAdvanceAGameThatHasNotStarted() {
		assertThatThrownBy { lobby.advancePhase(FakeDice(2, 1)) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game hasn't started")
	}

	@Test
	fun shouldNotAdvanceAFinishedGame() {
		val finished = (1..10).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

		assertThatThrownBy { finished.advancePhase(FakeDice(2, 1)) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game is over")
	}

	@Test
	fun shouldRecordAPlayerAsDoneWhileOthersStillBid() {
		val game = started.signalDoneBidding("joiner@example.com", FakeDice(2, 1))

		assertThat(
			game.turn
		).isEqualTo(
			Turn.bidding(1).copy(doneBidding = listOf("joiner@example.com"))
		)
	}

	@Test
	fun shouldCloseBiddingTheMomentTheLastPlayerSignalsDone() {
		val game = started.signalDoneBidding("joiner@example.com", FakeDice(2, 1)).signalDoneBidding("creator@example.com", FakeDice(2, 1))

		assertThat(
			game.turn
		).isEqualTo(
			Turn.bidding(1).copy(
				phase = TurnPhase.SHIP_JUMPS,
				initiative = listOf("creator@example.com", "joiner@example.com"),
				initiativeRolls = listOf(InitiativeRoll("creator@example.com", 0, 0, listOf(2)), InitiativeRoll("joiner@example.com", 0, 0, listOf(1)))
			)
		)
	}

	@Test
	fun shouldIgnoreASecondDoneFromTheSamePlayer() {
		val once = started.signalDoneBidding("joiner@example.com", FakeDice(2, 1))

		assertThat(once.signalDoneBidding("joiner@example.com", FakeDice(2, 1))).isEqualTo(once)
	}

	@Test
	fun shouldRejectDoneOnceBiddingHasClosed() {
		val closed = started.signalDoneBidding("joiner@example.com", FakeDice(2, 1)).signalDoneBidding("creator@example.com", FakeDice(2, 1))

		assertThatThrownBy { closed.signalDoneBidding("joiner@example.com", FakeDice(2, 1)) }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Bidding has closed")
	}

	@Test
	fun shouldRejectDoneFromSomeoneWhoIsNotInTheGame() {
		assertThatThrownBy { started.signalDoneBidding("stranger@example.com", FakeDice(2, 1)) }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldRejectDoneBeforeTheGameHasStarted() {
		assertThatThrownBy { lobby.signalDoneBidding("joiner@example.com", FakeDice(2, 1)) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game hasn't started")
	}
}
