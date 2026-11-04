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

// E04-S01: picking places in the turn order, one at a time in initiative order (R26, R27).
class GameTurnOrderTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	// Nobody bids; d2 rolls in join order put Kim (2) first, then a tie of Ada and Grace (1) rerolled: Grace 2, Ada 1.
	private val shipJumps = Game.lobby("game-1", 3, ada).addPlayer(grace).addPlayer(kim)
		.start(
			"ada@example.com",
			FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to EPSILON_ERIDANI)
		)
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
		val game = shipJumps.pickTurnOrderPlace("kim@example.com", 3)

		assertThat(game.turn!!.turnOrder()).containsExactly(null, null, "kim@example.com")
		assertThat(game.turn!!.picker()).isEqualTo("grace@example.com")
	}

	@Test
	fun shouldGiveTheLastPlayerTheRemainingPlaceWithoutAsking() {
		val game = shipJumps.pickTurnOrderPlace("kim@example.com", 3).pickTurnOrderPlace("grace@example.com", 1)

		// S01-6: Ada is the only one left, so she gets 2nd.
		assertThat(game.turn!!.turnOrder()).containsExactly("grace@example.com", "ada@example.com", "kim@example.com")
		assertThat(game.turn!!.picker()).isNull()
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.SHIP_JUMPS)
	}

	@Test
	fun shouldLogEachPickAndThenTheFullOrder() {
		val game = shipJumps.pickTurnOrderPlace("kim@example.com", 3).pickTurnOrderPlace("grace@example.com", 1)

		assertThat(game.log.takeLast(4)).containsExactly(
			LogEntry(1, "Kessler Cargo chose 3rd"),
			LogEntry(1, "Vega Lines chose 1st"),
			LogEntry(1, "Orion Freight gets 2nd, the only place left"),
			LogEntry(1, "Turn order (for this turn): Vega Lines → Orion Freight → Kessler Cargo")
		)
	}

	@Test
	fun shouldGiveTheSecondOfTwoPlayersTheOtherPlaceAtOnce() {
		val two = Game.lobby("game-2", 3, ada).addPlayer(grace)
			.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))
			.advancePhase(FakeDice(2, 1))

		val game = two.pickTurnOrderPlace("ada@example.com", 2)

		assertThat(game.turn!!.turnOrder()).containsExactly("grace@example.com", "ada@example.com")
	}

	@Test
	fun shouldRefuseAPickOutOfTurn() {
		assertThatThrownBy { shipJumps.pickTurnOrderPlace("ada@example.com", 1) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Kessler Cargo's turn to choose a place.")
	}

	@Test
	fun shouldRefuseATakenMissingOrOutOfRangePlace() {
		val afterKim = shipJumps.pickTurnOrderPlace("kim@example.com", 3)

		listOf(3, 0, 4, null).forEach { place ->
			assertThatThrownBy { afterKim.pickTurnOrderPlace("grace@example.com", place) }
				.isInstanceOf(InvalidPlaceException::class.java)
				.hasMessage("Choose a free place in the turn order.")
		}
	}

	@Test
	fun shouldRefuseAPickOnceTheOrderIsSet() {
		val set = shipJumps.pickTurnOrderPlace("kim@example.com", 3).pickTurnOrderPlace("grace@example.com", 1)

		assertThatThrownBy { set.pickTurnOrderPlace("ada@example.com", 2) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("The turn order is already set.")
	}

	@Test
	fun shouldRefuseAPickOutsideShipJumps() {
		val bidding = Game.lobby("game-3", 3, ada).addPlayer(grace)
			.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))

		assertThatThrownBy { bidding.pickTurnOrderPlace("ada@example.com", 1) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Ship-jumps hasn't started.")
		assertThatThrownBy { shipJumps.advancePhase(FakeDice()).pickTurnOrderPlace("kim@example.com", 1) }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Ship-jumps has closed")
	}

	@Test
	fun shouldRefuseSomeoneWhoIsNotInTheGame() {
		assertThatThrownBy { shipJumps.pickTurnOrderPlace("stranger@example.com", 1) }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldStartTheNextTurnWithNoPicks() {
		val set = shipJumps.pickTurnOrderPlace("kim@example.com", 3).pickTurnOrderPlace("grace@example.com", 1)

		val nextBidding = set.advancePhase(FakeDice()).advancePhase(FakeDice())

		assertThat(nextBidding.turn!!.turnOrderPicks).isEmpty()
	}
}
