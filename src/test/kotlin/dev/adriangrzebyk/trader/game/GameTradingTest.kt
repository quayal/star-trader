package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E05-S01: the first initiative player orders and triggers the resolutions (R25, R36).
class GameTradingTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	// Initiative Grace, Ada: Ada rolls 1 and Grace 2 on d2, with no bids.
	private val shipJumps = Game.lobby("game-1", 3, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))
		.advancePhase(FakeDice(1, 2))
		.pickTurnOrderPlace("grace@example.com", 1)

	private val trading = shipJumps.finishJumping("grace@example.com").finishJumping("ada@example.com")

	@Test
	fun shouldQueueEveryGoodWhenTheLastJumpSlotEnds() {
		assertThat(shipJumps.turn!!.initiative).containsExactly("grace@example.com", "ada@example.com")
		assertThat(trading.turn!!.phase).isEqualTo(TurnPhase.TRADING)
		assertThat(trading.turn!!.tradingQueue).isEqualTo(MarketGood.everyOnBoard())
	}

	@Test
	fun shouldResolveTheNextGoodAndLogWhoChoseIt() {
		// S01-4, S01-5
		val game = trading.resolveNextGood("grace@example.com")

		assertThat(game.turn!!.resolvedGoods).containsExactly(MarketGood(GAMMA_LEPORIS, Goods.POLY))
		assertThat(game.log.last()).isEqualTo(LogEntry(1, "Resolving: Gamma Leporis POLY (chosen by Vega Lines)"))
	}

	@Test
	fun shouldResolveInTheOrderTheFirstInInitiativeChose() {
		// S01-3: reordering isn't logged; the resolution is
		val order = listOf(MarketGood(MU_HERCULIS, Goods.ISO)) + trading.turn!!.tradingQueue.minus(MarketGood(MU_HERCULIS, Goods.ISO))

		val reordered = trading.reorderTradingQueue("grace@example.com", order)
		val game = reordered.resolveNextGood("grace@example.com")

		assertThat(reordered.log).isEqualTo(trading.log)
		assertThat(game.log.last().text).isEqualTo("Resolving: Mu Herculis ISO (chosen by Vega Lines)")
	}

	@Test
	fun shouldRefuseAnyoneButTheFirstInInitiative() {
		// R25, R04
		assertThatThrownBy { trading.resolveNextGood("ada@example.com") }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("Order decided by Vega Lines (first in initiative).")
		assertThatThrownBy { trading.reorderTradingQueue("ada@example.com", trading.turn!!.tradingQueue.reversed()) }
			.isInstanceOf(NotYourTurnException::class.java)
	}

	@Test
	fun shouldRefuseSomeoneOutsideTheGame() {
		assertThatThrownBy { trading.resolveNextGood("kim@example.com") }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldRefuseToResolveBeforeTrading() {
		assertThatThrownBy { shipJumps.resolveNextGood("grace@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Trading hasn't started.")
	}
}
