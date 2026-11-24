package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E13-S01, S02 (connections), S03: reputation starts at 20, recovers and pays a bonus at a turn's end, moves with
// connection raises, and can be bought (R85–R88). Market positions and battle are in GamePositionRollTest and GameBattleTest.
class GameReputationTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	private fun investing(turns: Int): Game {
		val started = Game.lobby("game-1", turns, ada).addPlayer(grace)
			.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE)
		return (1..5).fold(ConnectionsPlaced(started).byEveryone()) { game, _ -> game.advancePhase(FakeDice(2, 1)) }
	}

	private val investing = investing(20)

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	/** [game] with Ada's reputation at [ada] and Grace's at [grace]. */
	private fun withReputations(game: Game, ada: Int, grace: Int): Game = game.copy(
		players = game.players.map {
			it.withReputationChanged((if (it.email == "ada@example.com") ada else grace) - it.reputation.value)
		}
	)

	private fun endTurn(game: Game, dice: Dice = NO_CONTROL_ROLLS): Game =
		game.signalDoneInvesting("ada@example.com", dice).signalDoneInvesting("grace@example.com", dice)

	@Test
	fun shouldStartEveryoneAtTwenty() {
		assertThat(investing.players.map { it.reputation.value }).containsOnly(20)
	}

	@Test
	fun shouldRecoverFromOneToSeventeenAtTheEndOfATurnButNotAbove() {
		// E13-S01-2: Reputation's own test covers 0, where a player has to buy their way back (creator, 2026-11-18)
		val next = endTurn(withReputations(investing, ada = 10, grace = 18))

		assertThat(playerOf(next, "ada@example.com").reputation.value).isEqualTo(13)
		assertThat(playerOf(next, "grace@example.com").reputation.value).isEqualTo(18)
		assertThat(next.log.map { it.text }).contains("Orion Freight's reputation: +3 for recovering → 13")
	}

	@Test
	fun shouldPayTheBonusFromTwentyFiveAndNameWhoWasPaidWithoutTheAmount() {
		// E13-S01-3: 27 pays 10 HT, 24 pays nothing
		val before = withReputations(investing, ada = 27, grace = 24)
		val next = endTurn(before)

		assertThat(playerOf(next, "ada@example.com").money).isEqualTo(playerOf(before, "ada@example.com").money + 10)
		assertThat(playerOf(next, "grace@example.com").money).isEqualTo(playerOf(before, "grace@example.com").money)
		assertThat(next.log.map { it.text }).contains("Reputation bonus: Orion Freight")
	}

	@Test
	fun shouldPayTheBonusOnTheLastTurnTooBeforeTheWinnerIsDecided() {
		// E13-S01-3 (creator, 2026-11-18): the bonus can decide a close game; no recovery after the last turn
		val last = withReputations(investing(1), ada = 40, grace = 5)
		val over = endTurn(last, FakeDice.d6(3))

		assertThat(over.status).isEqualTo(GameStatus.FINISHED)
		assertThat(playerOf(over, "ada@example.com").money).isEqualTo(playerOf(last, "ada@example.com").money + 40)
		assertThat(playerOf(over, "grace@example.com").reputation.value).isEqualTo(5)
		assertThat(over.log.takeLast(2).map { it.text }.first()).isEqualTo("Reputation bonus: Orion Freight")
	}

	@Test
	fun shouldMoveReputationWithEachConnectionRaise() {
		// E13-S02: business +2, political +1, criminal −1
		val business = investing.raiseConnection("ada@example.com", ConnectionKind.BUSINESS)
		val criminal = investing.raiseConnection("grace@example.com", ConnectionKind.CRIMINAL)

		assertThat(playerOf(business, "ada@example.com").reputation.value).isEqualTo(22)
		assertThat(playerOf(criminal, "grace@example.com").reputation.value).isEqualTo(19)
		assertThat(criminal.log.last().text).isEqualTo("Vega Lines's reputation: −1 for raising criminal connections → 19")
	}

	@Test
	fun shouldBuyReputationOncePerInvestmentForTwentyHt() {
		// E13-S03
		val before = playerOf(investing, "ada@example.com")
		val bought = investing.buyReputation("ada@example.com", FakeDice.d6(4))

		assertThat(playerOf(bought, "ada@example.com").reputation.value).isEqualTo(24)
		assertThat(playerOf(bought, "ada@example.com").money).isEqualTo(before.money - 20)
		assertThat(bought.log.last().text).isEqualTo("Orion Freight's reputation: +4 for buying reputation (rolled 4) → 24")
		assertThatThrownBy { bought.buyReputation("ada@example.com", FakeDice.d6(4)) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("You've already bought reputation this Investment.")
	}

	@Test
	fun shouldRefuseBuyingAtFortyOrWithTooLittleMoney() {
		assertThatThrownBy { withReputations(investing, ada = 40, grace = 20).buyReputation("ada@example.com", FakeDice.d6(4)) }
			.isInstanceOf(InvalidInvestmentException::class.java)
			.hasMessage("Your reputation is already at 40.")
		val broke = investing.copy(players = investing.players.map { if (it.email == "ada@example.com") it.pay(it.money - 15, "Test") else it })
		assertThatThrownBy { broke.buyReputation("ada@example.com", FakeDice.d6(4)) }
			.hasMessage("Buying reputation costs 20 HT; you have 15 HT.")
		assertThatThrownBy { investing(20).copy(turn = Turn.bidding(1)).buyReputation("ada@example.com", FakeDice.d6(4)) }
			.hasMessage("Investment isn't open.")
	}
}
