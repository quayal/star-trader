package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.trading.SupplyDemand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// E05-S07: the turn ends once its last good has resolved, and the next starts with Bidding (N06, R39).
class GameTurnEndTest {

	private val gammaLeporisPoly = MarketGood(GAMMA_LEPORIS, Goods.POLY)

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	private fun started(turns: Int): Game = Game.lobby("game-1", turns, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))

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
			.pickTurnOrderPlace("grace@example.com", 1)
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

	@Test
	fun shouldStayInTradingWhileTheLastGoodIsStillResolving() {
		val game = lastGoodWaitingForGrace(3)

		assertThat(game.turn!!.number).isEqualTo(1)
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.TRADING)
		assertThat(game.turn!!.tradingQueue).isEmpty()
		assertThat(game.turn!!.isEveryGoodResolved()).isFalse()
	}

	@Test
	fun shouldStartTheNextTurnWithBiddingOnceTheLastGoodHasResolved() {
		// S07-1, S07-2: the last trade finishes the last good, and the turn ends in the same step
		val game = lastGoodWaitingForGrace(3).trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

		assertThat(game.status).isEqualTo(GameStatus.RUNNING)
		assertThat(game.turn).isEqualTo(Turn.bidding(2))
	}

	@Test
	fun shouldLetOffersThatDidNotTradeExpire() {
		// S07-2 (proposed): the next turn starts with no offers, whether they traded or not
		val game = lastGoodWaitingForGrace(3).trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

		assertThat(game.turn!!.offers).isEmpty()
		assertThat(game.turn!!.offersOf("grace@example.com")).isEmpty()
	}

	@Test
	fun shouldEndTheTurnWhenTheLastGoodHasNoOffersToo() {
		// A last good with no offers, no Dictator and no Wholesaler finishes on Resolve next itself
		val game = withOneGoodLeft(trading(3, started(3)))
			.resolveNextGood("grace@example.com", FakeDice.twoD6(7))

		assertThat(game.turn).isEqualTo(Turn.bidding(2))
	}

	@Test
	fun shouldLogTheEndOfTheTurnAndStartANewGroupForTheNextOne() {
		// S07-3: the new turn's first line is logged under its own number, so the log starts a new group
		val game = lastGoodWaitingForGrace(3).trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

		assertThat(game.log.takeLast(2)).containsExactly(
			LogEntry(1, "All goods resolved"),
			LogEntry(2, "Turn 2 of 3 starts with Bidding; turn 1's offers have expired")
		)
	}

	@Test
	fun shouldKeepTheMovedPricesIntoTheNextTurn() {
		// R35: only the price carries over. Grace bought 1, counter 2, so Gamma Leporis POLY moves from 2 HT by modifier(2)
		val game = lastGoodWaitingForGrace(3).trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

		assertThat(game.prices.priceOf(gammaLeporisPoly)).isEqualTo((2 + SupplyDemand.priceModifierAt(2)).coerceAtLeast(1))
	}

	@Test
	fun shouldNotStartAnotherTurnAfterTheLastOne() {
		// R39, R41: a one-turn game is over after its first Trading, and keeps its last turn
		val game = lastGoodWaitingForGrace(1).trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

		assertThat(game.status).isEqualTo(GameStatus.FINISHED)
		assertThat(game.turn!!.number).isEqualTo(1)
		assertThat(game.log.map { it.text }).contains("All goods resolved").noneMatch { it.startsWith("Turn 2") }
	}
}
