package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.board.SupplyDemand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// E05-S07, E06-S01: once its last good has resolved Investment starts, and the turn ends when everyone is done investing (R44, R39).
class GameTurnEndTest {

	private val gammaLeporisPoly = MarketGood(GAMMA_LEPORIS, Goods.POLY)

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	private fun started(turns: Int): Game = Game.lobby("game-1", turns, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
			ConnectionsPlaced(it).byEveryone()
		}

	/**
	 * A game of [turns] turns in turn 1's Trading, Grace first in initiative, her Meridian in port at Gamma Leporis.
	 * Unless [bidding] says otherwise, Grace has an offer to buy Gamma Leporis POLY at 11 HT, which resolves last.
	 */
	private fun trading(
		turns: Int,
		bidding: Game = started(turns).placeOffer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11)
	): Game {
		val game = bidding
			.advancePhase(FakeDice(1, 2))
			.pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)
			.finishJumping("grace@example.com")
			.finishJumping("ada@example.com")
		val queue = game.turn!!.tradingQueue
		return game.reorderTradingQueue("grace@example.com", queue.minus(gammaLeporisPoly) + gammaLeporisPoly)
	}

	/** [game] after every good but the last has resolved: none of them has an offer, so each resolves at once (roll 7). */
	private fun withOneGoodLeft(game: Game): Game = (1 until MarketGood.everyOnBoard().size).fold(game) { resolving, _ ->
		resolving.resolveNextGood("grace@example.com", FakeDice.twoD6(7))
	}

	// Gamma Leporis POLY resolves last: roll 2, counter 3, and Grace's buy waits for her amount.
	private fun lastGoodWaitingForGrace(turns: Int): Game =
		withOneGoodLeft(trading(turns)).resolveNextGood("grace@example.com", FakeDice.twoD6(2))

	/** [game] once Grace and Ada have both signalled done investing: the last signal ends the turn (R44). */
	private fun afterInvesting(game: Game): Game =
		game.signalDoneInvesting("grace@example.com", NO_CONTROL_ROLLS).signalDoneInvesting("ada@example.com", NO_CONTROL_ROLLS)

	/** Turn 1's Trading, finished: Grace's last trade has resolved the last good, and Opportunity has started (R102). */
	private fun opportunity(turns: Int): Game =
		lastGoodWaitingForGrace(turns).trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

	/** Turn 1's Investment: everyone was done with opportunities. */
	private fun investing(turns: Int): Game = OpportunityPassed(opportunity(turns)).byEveryone()

	@Test
	fun shouldStayInTradingWhileTheLastGoodIsStillResolving() {
		val game = lastGoodWaitingForGrace(3)

		assertThat(game.turn!!.number).isEqualTo(1)
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.TRADING)
		assertThat(game.turn!!.tradingQueue).isEmpty()
		assertThat(game.turn!!.isEveryGoodResolved()).isFalse()
	}

	@Test
	fun shouldStartOpportunityOnceTheLastGoodHasResolvedThenInvestment() {
		// R102: the last trade finishes the last good, and Opportunity starts in the same step, Grace first in the order
		val game = opportunity(3)

		assertThat(game.status).isEqualTo(GameStatus.RUNNING)
		assertThat(game.turn!!.number).isEqualTo(1)
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.OPPORTUNITY)
		assertThat(game.turn!!.opportunist()).isEqualTo("grace@example.com")
		assertThat(game.log.last()).isEqualTo(LogEntry.event(1, "All goods resolved"))
		val investing = investing(3)
		assertThat(investing.turn!!.phase).isEqualTo(TurnPhase.INVESTMENT)
		assertThat(investing.turn!!.doneInvesting).isEmpty()
	}

	@Test
	fun shouldWaitInInvestmentUntilEveryoneIsDone() {
		// E06-S01-2, D09
		val oneDone = investing(3).signalDoneInvesting("grace@example.com", NO_CONTROL_ROLLS)

		assertThat(oneDone.turn!!.phase).isEqualTo(TurnPhase.INVESTMENT)
		assertThat(oneDone.turn!!.doneInvesting).containsExactly("grace@example.com")
	}

	@Test
	fun shouldStartTheNextTurnWithBiddingOnceEveryoneIsDoneInvesting() {
		// S07-1, S07-2, E06-S01-2
		val game = afterInvesting(investing(3))

		assertThat(game.status).isEqualTo(GameStatus.RUNNING)
		assertThat(game.turn).isEqualTo(Turn.bidding(2))
	}

	@Test
	fun shouldLetOffersThatDidNotTradeExpire() {
		// S07-2 (proposed): the next turn starts with no offers, whether they traded or not
		val game = afterInvesting(investing(3))

		assertThat(game.turn!!.offers).isEmpty()
		assertThat(game.turn!!.offersOf("grace@example.com")).isEmpty()
	}

	@Test
	fun shouldEndTheTurnWhenTheLastGoodHasNoOffersToo() {
		// A last good with no offers, no Dictator and no Wholesaler finishes on Resolve next itself
		val game = withOneGoodLeft(trading(3, started(3)))
			.resolveNextGood("grace@example.com", FakeDice.twoD6(7))

		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.OPPORTUNITY)
		assertThat(afterInvesting(OpportunityPassed(game).byEveryone()).turn).isEqualTo(Turn.bidding(2))
	}

	@Test
	fun shouldLogTheEndOfTheTurnButNotTheStartOfTheNextOne() {
		// Creator, 2026-11-19: the log groups by turn and the top bar shows it, so no line says a turn starts.
		val game = afterInvesting(investing(3))

		assertThat(game.turn!!.number).isEqualTo(2)
		assertThat(game.log.last()).isEqualTo(LogEntry.event(1, "All goods resolved"))
	}

	@Test
	fun shouldKeepTheMovedPricesIntoTheNextTurn() {
		// R35: only the price carries over. Grace bought 1, counter 2, so Gamma Leporis POLY moves from 2 HT by modifier(2)
		val game = lastGoodWaitingForGrace(3).trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

		assertThat(game.prices.priceOf(gammaLeporisPoly)).isEqualTo((2 + SupplyDemand.priceModifierAt(2)).coerceAtLeast(1))
	}

	@Test
	fun shouldNotStartAnotherTurnAfterTheLastOne() {
		// R39, R41, R44: a one-turn game is over after its first Investment, and keeps its last turn
		val game = afterInvesting(investing(1))

		assertThat(game.status).isEqualTo(GameStatus.FINISHED)
		assertThat(game.turn!!.number).isEqualTo(1)
		assertThat(game.log.map { it.text }).contains("All goods resolved").noneMatch { it.startsWith("Turn 2") }
	}
}
