package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E12-S01, E12-S02: placing the starting connection points in turn 1's Bidding, and raising one per Investment (R82, R83).
class GameConnectionsTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	/** Ada rolls 2 (5 points), Grace 1 (4 points). */
	private val started = Game.lobby("game-1", 20, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), FakeDice.d3(2, 1))

	private val placed = started.placeConnections("ada@example.com", 2, 3, 0).placeConnections("grace@example.com", 0, 0, 4)

	private val investing = (1..5).fold(placed) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	@Test
	fun shouldLogTheRollsAtTheStart() {
		// E12-S01-1
		assertThat(started.log.map { it.text }).contains(
			"Orion Freight rolled 2: 5 connection points to place",
			"Vega Lines rolled 1: 4 connection points to place"
		)
	}

	@Test
	fun shouldPlaceThePointsPubliclyAndFinally() {
		// E12-S01-2
		val game = started.placeConnections("ada@example.com", 2, 3, 0)

		assertThat(playerOf(game, "ada@example.com").connections).isEqualTo(Connections(2, 3, 0))
		assertThat(playerOf(game, "ada@example.com").connectionPoints).isZero()
		assertThat(game.log.last().text).isEqualTo("Orion Freight's connections: political 2, business 3, criminal 0")
		assertThatThrownBy { game.placeConnections("ada@example.com", 5, 0, 0) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("You have already placed your connection points.")
	}

	@Test
	fun shouldRefuseDoneBiddingUntilThePointsArePlaced() {
		// E12-S01-3: the gate; a player who never places holds Bidding open like anyone not done (D09)
		assertThatThrownBy { started.signalDoneBidding("ada@example.com", FakeDice()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Place your 5 connection points first.")

		val adaDone = started.placeConnections("ada@example.com", 2, 3, 0).signalDoneBidding("ada@example.com", FakeDice())

		assertThat(adaDone.turn!!.doneBidding).containsExactly("ada@example.com")
		assertThatThrownBy { adaDone.signalDoneBidding("grace@example.com", FakeDice()) }
			.hasMessage("Place your 4 connection points first.")
	}

	@Test
	fun shouldRefuseAPlacementOutsideBidding() {
		assertThatThrownBy { investing.placeConnections("ada@example.com", 5, 0, 0) }
			.isInstanceOf(PhaseClosedException::class.java)
	}

	@Test
	fun shouldRaiseOneConnectionForTheNewLevelTimesTenAndLogItWithoutTheCost() {
		// E12-S02-4, S02-5: political 2 → 3 costs 30 HT
		val game = investing.raiseConnection("ada@example.com", ConnectionKind.POLITICAL)

		assertThat(playerOf(game, "ada@example.com").connections).isEqualTo(Connections(3, 3, 0))
		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(playerOf(investing, "ada@example.com").money - 30)
		assertThat(game.log.takeLast(2)).containsExactly(
			LogEntry.event(1, "Orion Freight raised political connections to 3"),
			// R86 (E13-S02): a political point is +1 reputation.
			LogEntry.event(1, "Orion Freight's reputation: +1 for raising political connections \u2192 21")
		)
	}

	@Test
	fun shouldRefuseASecondRaiseInTheSameInvestmentOfAnyKind() {
		val raised = investing.raiseConnection("ada@example.com", ConnectionKind.POLITICAL)

		for (kind in ConnectionKind.entries) {
			assertThatThrownBy { raised.raiseConnection("ada@example.com", kind) }
				.isInstanceOf(InvalidGameOperationException::class.java)
				.hasMessage("You've already raised a connection this Investment.")
		}
		assertThat(raised.raiseConnection("grace@example.com", ConnectionKind.CRIMINAL).turn!!.connectionsRaised)
			.containsExactly("ada@example.com", "grace@example.com")
	}

	@Test
	fun shouldLetThemRaiseAgainInTheNextInvestment() {
		val raised = investing.raiseConnection("ada@example.com", ConnectionKind.POLITICAL)
		val nextInvestment = raised.signalDoneInvesting(
			"ada@example.com",
			NO_CONTROL_ROLLS
		).signalDoneInvesting("grace@example.com", NO_CONTROL_ROLLS)
			.signalDoneBidding("ada@example.com", FakeDice()).signalDoneBidding("grace@example.com", FakeDice(2, 1))
			.advancePhase(FakeDice()).advancePhase(FakeDice()).advancePhase(FakeDice()).advancePhase(FakeDice())

		assertThat(nextInvestment.turn!!.number).isEqualTo(2)
		assertThat(nextInvestment.turn!!.connectionsRaised).isEmpty()
		val again = nextInvestment.raiseConnection("ada@example.com", ConnectionKind.POLITICAL)
		assertThat(playerOf(again, "ada@example.com").connections.political).isEqualTo(4)
	}

	@Test
	fun shouldRefuseARaiseAtTenOrBeyondTheirMoney() {
		val maxed = investing.copy(
			players = investing.players.map { if (it.email == "ada@example.com") it.copy(connections = Connections(10, 3, 0), money = 35) else it }
		)

		assertThatThrownBy { maxed.raiseConnection("ada@example.com", ConnectionKind.POLITICAL) }
			.isInstanceOf(InvalidConnectionsException::class.java)
			.hasMessage("Your political connections are already at 10.")
		assertThatThrownBy { maxed.raiseConnection("ada@example.com", ConnectionKind.BUSINESS) }
			.isInstanceOf(InvalidConnectionsException::class.java)
			.hasMessage("Raising business connections to 4 costs 40 HT; you have 35 HT.")
		assertThat(maxed.turn!!.connectionsRaised).isEmpty()
	}

	@Test
	fun shouldRefuseARaiseOutsideInvestment() {
		assertThatThrownBy { placed.raiseConnection("ada@example.com", ConnectionKind.POLITICAL) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}
}
