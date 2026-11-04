package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameInitiativeTest {

	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val third = Player.newcomer("third@example.com", "Kim", "Kessler Cargo")
	private val started = Game.lobby("game-1", 2, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))
	private val startedWithThree = Game.lobby("game-2", 2, creator).addPlayer(joiner).addPlayer(third).start(
		"creator@example.com",
		FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to EPSILON_ERIDANI)
	)

	@Test
	fun shouldPlaceABidDuringBidding() {
		val game = started.placeInitiativeBid("joiner@example.com", 40)

		assertThat(game.turn?.bids).containsExactly(InitiativeBid("joiner@example.com", 40))
	}

	@Test
	fun shouldReplaceAnEarlierBidWhenTheBidIsChanged() {
		val game = started.placeInitiativeBid("joiner@example.com", 40).placeInitiativeBid("joiner@example.com", 25)

		assertThat(game.turn?.bids).containsExactly(InitiativeBid("joiner@example.com", 25))
	}

	@Test
	fun shouldGiveEachPlayerOnlyTheirOwnBid() {
		val game = started.placeInitiativeBid("joiner@example.com", 40).placeInitiativeBid("creator@example.com", 10)

		assertThat(game.turn?.bidOf("joiner@example.com")).isEqualTo(InitiativeBid("joiner@example.com", 40))
		assertThat(game.turn?.bidOf("creator@example.com")).isEqualTo(InitiativeBid("creator@example.com", 10))
	}

	@Test
	fun shouldAcceptNothingAndEverythingThePlayerHas() {
		assertThat(started.placeInitiativeBid("joiner@example.com", 0).turn?.bidOf("joiner@example.com")?.amount).isEqualTo(0)
		assertThat(started.placeInitiativeBid("joiner@example.com", 300).turn?.bidOf("joiner@example.com")?.amount).isEqualTo(300)
	}

	@Test
	fun shouldRejectABidThatIsMissingOrNotAWholeNumber() {
		assertThatThrownBy { started.placeInitiativeBid("joiner@example.com", null) }
			.isInstanceOf(InvalidBidException::class.java)
			.hasMessage("Enter a whole number of HT, 0 or more.")
	}

	@Test
	fun shouldRejectANegativeBid() {
		assertThatThrownBy { started.placeInitiativeBid("joiner@example.com", -1) }
			.isInstanceOf(InvalidBidException::class.java)
			.hasMessage("A bid can't be negative. Enter 0 to bid nothing.")
	}

	@Test
	fun shouldRejectABidOverThePlayersMoney() {
		assertThatThrownBy { started.placeInitiativeBid("joiner@example.com", 301) }
			.isInstanceOf(InvalidBidException::class.java)
			.hasMessage("You have 300 HT. Enter 300 or less.")
	}

	@Test
	fun shouldStillTakeABidAfterThePlayerSignalledDone() {
		// S04-2, R17: done is a readiness flag, not a commit.
		val game = started.signalDoneBidding("joiner@example.com", FakeDice()).placeInitiativeBid("joiner@example.com", 40)

		assertThat(game.turn?.bids).containsExactly(InitiativeBid("joiner@example.com", 40))
	}

	@Test
	fun shouldRejectABidOnceBiddingHasClosed() {
		val closed = started.advancePhase(FakeDice(1, 2))

		assertThatThrownBy { closed.placeInitiativeBid("joiner@example.com", 40) }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Bidding has closed")
	}

	@Test
	fun shouldRejectABidFromSomeoneWhoIsNotInTheGame() {
		assertThatThrownBy { started.placeInitiativeBid("stranger@example.com", 40) }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldChargeEveryBidWhenBiddingClosesWinOrLose() {
		val game = started.placeInitiativeBid("joiner@example.com", 40).placeInitiativeBid("creator@example.com", 10)
			.advancePhase(FakeDice(1, 1))

		assertThat(game.players.map { it.email to it.money })
			.containsExactly("creator@example.com" to 290, "joiner@example.com" to 260)
	}

	@Test
	fun shouldChargeOnlyTheLastBidOfAPlayerWhoChangedIt() {
		val game = started.placeInitiativeBid("joiner@example.com", 40).placeInitiativeBid("joiner@example.com", 15)
			.advancePhase(FakeDice(1, 1))

		assertThat(game.players.first { it.email == "joiner@example.com" }.money).isEqualTo(285)
	}

	@Test
	fun shouldOrderEveryPlayerByBidPlusD2HighestFirst() {
		// Rolls are taken in join order: creator 10 + 2 = 12, joiner 12 + 1 = 13.
		val game = started.placeInitiativeBid("creator@example.com", 10).placeInitiativeBid("joiner@example.com", 12)
			.advancePhase(FakeDice(2, 1))

		assertThat(game.turn?.initiative).containsExactly("joiner@example.com", "creator@example.com")
	}

	@Test
	fun shouldTreatAPlayerWhoNeverBidAsBiddingZeroAndStillRollThem() {
		// Q07 (proposed): no bid is a bid of 0; the creator's 0 + 2 beats the joiner's 0 + 1.
		val game = started.placeInitiativeBid("joiner@example.com", 0).advancePhase(FakeDice(2, 1))

		assertThat(game.turn?.initiative).containsExactly("creator@example.com", "joiner@example.com")
		assertThat(game.players.map { it.money }).containsExactly(300, 300)
	}

	@Test
	fun shouldRerollATieUntilItIsBroken() {
		// 5 + 1 and 5 + 1 tie, then 1 and 1 tie again, then the joiner's 2 beats the creator's 1.
		val game = started.placeInitiativeBid("creator@example.com", 5).placeInitiativeBid("joiner@example.com", 5)
			.advancePhase(FakeDice(1, 1, 1, 1, 1, 2))

		assertThat(game.turn?.initiative).containsExactly("joiner@example.com", "creator@example.com")
	}

	@Test
	fun shouldRerollOnlyThePlayersWhoTiedAndOnlyAmongThemselves() {
		// Creator 10 + 1 = 11 leads; joiner 0 + 2 and third 0 + 2 tie, so only they roll again: joiner 1, third 2.
		val game = startedWithThree.placeInitiativeBid("creator@example.com", 10).advancePhase(FakeDice(1, 2, 2, 1, 2))

		assertThat(game.turn?.initiative).containsExactly("creator@example.com", "third@example.com", "joiner@example.com")
	}

	@Test
	fun shouldLogTheInitiativeOrderWithoutAnyAmounts() {
		val game = started.placeInitiativeBid("joiner@example.com", 40).advancePhase(FakeDice(1, 1))

		// R29, S06-3: the log is public, so it carries the order and never a bid or money.
		assertThat(game.log.last()).isEqualTo(LogEntry(1, "Initiative order (bid + d2): Vega Lines → Orion Freight"))
	}

	@Test
	fun shouldLogALaterTurnsInitiativeOrderUnderThatTurn() {
		val turnTwo = started.advancePhase(FakeDice(1, 2)).advancePhase(FakeDice()).advancePhase(FakeDice())

		val closed = turnTwo.advancePhase(FakeDice(2, 1))

		// S06-1: the log is grouped by turn, so each line carries the turn it happened in.
		assertThat(closed.log.last()).isEqualTo(LogEntry(2, "Initiative order (bid + d2): Orion Freight → Vega Lines"))
	}

	@Test
	fun shouldKeepTheBidsAndOrderThroughTheTurnAndStartTheNextTurnWithNone() {
		val shipJumps = started.placeInitiativeBid("joiner@example.com", 40).advancePhase(FakeDice(1, 1))
		val trading = shipJumps.advancePhase(FakeDice())
		val nextBidding = trading.advancePhase(FakeDice())

		assertThat(trading.turn?.initiative).containsExactly("joiner@example.com", "creator@example.com")
		assertThat(trading.turn?.bids).containsExactly(InitiativeBid("joiner@example.com", 40))
		assertThat(nextBidding.turn).isEqualTo(Turn.bidding(2))
	}

	@Test
	fun shouldCloseBiddingWithTheChargeAndOrderWhenTheLastPlayerSignalsDone() {
		val game = started.placeInitiativeBid("creator@example.com", 30)
			.signalDoneBidding("joiner@example.com", FakeDice())
			.signalDoneBidding("creator@example.com", FakeDice(1, 1))

		assertThat(game.turn?.phase).isEqualTo(TurnPhase.SHIP_JUMPS)
		assertThat(game.turn?.initiative).containsExactly("creator@example.com", "joiner@example.com")
		assertThat(game.players.first { it.email == "creator@example.com" }.money).isEqualTo(270)
	}
}
