package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E04-S01: picking places in the turn order, one at a time in initiative order (R26, R27).
class GameTurnOrderTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	// Nobody bids; d2 rolls in join order put Kim (2) first, then a tie of Ada and Grace (1) rerolled: Grace 2, Ada 1.
	private val shipJumps = Game.lobby("game-1", 3, ada).addPlayer(grace).addPlayer(kim)
		.start(
			"ada@example.com",
			FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to EPSILON_ERIDANI),
			START_DICE
		).let { ConnectionsPlaced(it).byEveryone() }
		.advancePhase(FakeDice(1, 1, 2, 1, 2))

	@Test
	fun shouldStartPickingWhenBiddingClosesWithEveryPlaceFree() {
		val turn = shipJumps.turn!!

		assertThat(turn.initiative).containsExactly("kim@example.com", "grace@example.com", "ada@example.com")
		assertThat(turn.turnOrder()).containsExactly(null, null, null)
		assertThat(turn.picker()).isEqualTo("kim@example.com")
	}

	@Test
	fun shouldHaveNoTurnOrderOrPickerDuringBidding() {
		val bidding = Turn.bidding(1)

		assertThat(bidding.turnOrder()).isEmpty()
		assertThat(bidding.picker()).isNull()
	}

	@Test
	fun shouldLetTheFirstPlayerTakeAnyPlaceIncludingLast() {
		val game = shipJumps.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS)

		assertThat(game.turn!!.turnOrder()).containsExactly(null, null, "kim@example.com")
		assertThat(game.turn!!.picker()).isEqualTo("grace@example.com")
	}

	@Test
	fun shouldGiveTheLastPlayerTheRemainingPlaceWithoutAsking() {
		val game = shipJumps.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS).pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)

		// S01-6: Ada is the only one left, so she gets 2nd.
		assertThat(game.turn!!.turnOrder()).containsExactly("grace@example.com", "ada@example.com", "kim@example.com")
		assertThat(game.turn!!.picker()).isNull()
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.JUMPS)
	}

	@Test
	fun shouldLogEachPickAndThenTheFullOrder() {
		val game = shipJumps.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS).pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)

		assertThat(game.log.takeLast(4)).containsExactly(
			LogEntry.event(1, "Kessler Cargo chose 3rd"),
			LogEntry.event(1, "Vega Lines chose 1st"),
			LogEntry.event(1, "Orion Freight gets 2nd, the only place left"),
			LogEntry.event(1, "Turn order (for this turn): Vega Lines → Orion Freight → Kessler Cargo")
		)
	}

	@Test
	fun shouldGiveTheSecondOfTwoPlayersTheOtherPlaceAtOnce() {
		val two = Game.lobby("game-2", 3, ada).addPlayer(grace)
			.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
				ConnectionsPlaced(it).byEveryone()
			}
			.advancePhase(FakeDice(2, 1))

		val game = two.pickTurnOrderPlace("ada@example.com", 2, NO_NEWS)

		assertThat(game.turn!!.turnOrder()).containsExactly("grace@example.com", "ada@example.com")
	}

	@Test
	fun shouldRefuseAPickOutOfTurn() {
		assertThatThrownBy { shipJumps.pickTurnOrderPlace("ada@example.com", 1, NO_NEWS) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Kessler Cargo's turn to choose a place.")
	}

	@Test
	fun shouldRefuseATakenMissingOrOutOfRangePlace() {
		val afterKim = shipJumps.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS)

		listOf(3, 0, 4, null).forEach { place ->
			assertThatThrownBy { afterKim.pickTurnOrderPlace("grace@example.com", place, NO_NEWS) }
				.isInstanceOf(InvalidPlaceException::class.java)
				.hasMessage("Choose a free place in the turn order.")
		}
	}

	@Test
	fun shouldRefuseAPickOnceTheOrderIsSet() {
		val set = shipJumps.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS).pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)

		assertThatThrownBy { set.pickTurnOrderPlace("ada@example.com", 2, NO_NEWS) }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Initiative has closed")
	}

	@Test
	fun shouldRefuseAPickOutsideShipJumps() {
		val bidding = Game.lobby("game-3", 3, ada).addPlayer(grace)
			.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
				ConnectionsPlaced(it).byEveryone()
			}

		assertThatThrownBy { bidding.pickTurnOrderPlace("ada@example.com", 1, NO_NEWS) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Initiative hasn't started.")
		assertThatThrownBy { shipJumps.advancePhase(FakeDice()).pickTurnOrderPlace("kim@example.com", 1, NO_NEWS) }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Initiative has closed")
	}

	@Test
	fun shouldRefuseSomeoneWhoIsNotInTheGame() {
		assertThatThrownBy { shipJumps.pickTurnOrderPlace("stranger@example.com", 1, NO_NEWS) }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldStartTheNextTurnWithNoPicks() {
		val set = shipJumps.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS).pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)

		val nextBidding = set.advancePhase(FakeDice()).advancePhase(FakeDice()).advancePhase(FakeDice()).advancePhase(FakeDice())

		assertThat(nextBidding.turn!!.turnOrderPicks).isEmpty()
	}
}
