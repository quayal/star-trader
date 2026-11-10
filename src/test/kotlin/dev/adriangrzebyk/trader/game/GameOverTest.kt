package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E05-S08: the game ends after the last turn's Trading and the most money wins (R39).
class GameOverTest {

	private val gammaLeporisPoly = MarketGood(GAMMA_LEPORIS, Goods.POLY)

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	private fun started(turns: Int, vararg others: Player): Game = others
		.fold(Game.lobby("game-1", turns, ada)) { game, player -> game.addPlayer(player) }
		.start(
			"ada@example.com",
			FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to TAU_CETI)
		)

	/**
	 * [bidding] taken to Trading with Grace first in initiative (d2: Grace rolls 2, the others 1, then Kim beats Ada) and
	 * Gamma Leporis POLY last, then every good but that one resolved: none has an offer, so each resolves at once.
	 */
	private fun lastGoodLeft(bidding: Game): Game {
		val closed = bidding.advancePhase(if (bidding.players.size == 3) FakeDice(1, 2, 1, 1, 2) else FakeDice(1, 2))
		val ordered = closed.turn!!.initiative.dropLast(1).withIndex()
			.fold(closed) { game, (index, email) -> game.pickTurnOrderPlace(email, index + 1) }
		val trading = ordered.turn!!.initiative.fold(ordered) { game, email -> game.finishJumping(email) }
		val queue = trading.turn!!.tradingQueue
		val lastPoly = trading.reorderTradingQueue("grace@example.com", queue.minus(gammaLeporisPoly) + gammaLeporisPoly)
		return (1 until MarketGood.everyOnBoard().size).fold(lastPoly) { game, _ -> game.resolveNextGood("grace@example.com", FakeDice.twoD6(7)) }
	}

	// A one-turn game: Grace buys 1 POLY at 11 HT on the last good (roll 2, counter 3), so she ends with 289 HT, Ada 300.
	private val graceBought = lastGoodLeft(
		started(1, grace).placeOffer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11)
	).resolveNextGood("grace@example.com", FakeDice.twoD6(2))

	private val over = graceBought.trade("grace@example.com", 1, listOf(ShipLoad("Meridian", 1)), FakeDice.twoD6())

	@Test
	fun shouldEndTheGameWhenTheLastTurnsLastGoodHasResolved() {
		// S08-1: no further turn starts; the game keeps its last turn
		assertThat(graceBought.status).isEqualTo(GameStatus.RUNNING)
		assertThat(over.status).isEqualTo(GameStatus.FINISHED)
		assertThat(over.turn!!.number).isEqualTo(1)
	}

	@Test
	fun shouldRankEveryPlayerByMoneyAloneAndNameTheWinner() {
		// S08-2: only cash counts; Grace's POLY is worth nothing
		assertThat(over.standings().map { it.corporationName to it.money }).containsExactly("Orion Freight" to 300, "Vega Lines" to 289)
		assertThat(over.winners().map { it.email }).containsExactly("ada@example.com")
	}

	@Test
	fun shouldEndTheLogWithTheWinner() {
		// S08-5
		assertThat(over.log.takeLast(2)).containsExactly(
			LogEntry(1, "All goods resolved"),
			LogEntry(1, "Game over: Orion Freight wins with 300 HT")
		)
	}

	@Test
	fun shouldLetPlayersTiedOnMoneyShareTheWin() {
		// S08-4 (proposed): nobody traded, so all three end with 300 HT
		val tied = lastGoodLeft(started(1, grace, kim)).resolveNextGood("grace@example.com", FakeDice.twoD6(7))

		assertThat(tied.status).isEqualTo(GameStatus.FINISHED)
		assertThat(tied.winners().map { it.corporationName }).containsExactly("Orion Freight", "Vega Lines", "Kessler Cargo")
		assertThat(tied.log.last().text).isEqualTo("Game over: Orion Freight, Vega Lines and Kessler Cargo win with 300 HT")
	}

	@Test
	fun shouldWriteLargeAmountsWithThousandsSeparators() {
		// S08-4's example: "Vega Lines and Kessler Cargo win with 2,310 HT"
		val rich = lastGoodLeft(started(1, grace, kim))
		val moneyed = rich.copy(
			players = rich.players.map {
				when (it.email) {
					"ada@example.com" -> it.pay(100)
					else -> it.receive(2010)
				}
			}
		)

		val game = moneyed.resolveNextGood("grace@example.com", FakeDice.twoD6(7))

		assertThat(game.log.last().text).isEqualTo("Game over: Vega Lines and Kessler Cargo win with 2,310 HT")
		assertThat(game.standings().map { it.money }).containsExactly(2310, 2310, 200)
	}

	@Test
	fun shouldRefuseEveryActionOnceTheGameIsOver() {
		// S08-6: nothing can change a finished game
		listOf<() -> Game>(
			{ over.placeOffer("ada@example.com", TAU_CETI, Goods.POLY, OfferType.BUY, 5) },
			{ over.placeInitiativeBid("ada@example.com", 5) },
			{ over.signalDoneBidding("ada@example.com", FakeDice(1)) },
			{ over.resolveNextGood("grace@example.com", FakeDice.twoD6(7)) },
			{ over.finishJumping("grace@example.com") }
		).forEach { action ->
			assertThatThrownBy { action() }.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("This game is over")
		}
	}
}
