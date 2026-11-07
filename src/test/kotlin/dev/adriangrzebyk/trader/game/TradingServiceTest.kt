package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TradingServiceTest {

	private val gameStore = FakeGameStore()
	private val service = TradingService(gameStore)
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")

	// Ada rolls 2, Grace 1: Ada is first in initiative and picks first.
	private val trading = Game.lobby("game-1", 20, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))
		.advancePhase(FakeDice(2, 1))
		.pickTurnOrderPlace("creator@example.com", 1)
		.finishJumping("creator@example.com")
		.finishJumping("joiner@example.com")

	@Test
	fun shouldStoreTheResolution() {
		gameStore.create(trading)

		val game = service.resolveNext("game-1", "creator@example.com")

		assertThat(game.turn!!.resolvedGoods).hasSize(1)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldStoreTheNewOrder() {
		gameStore.create(trading)
		val reversed = trading.turn!!.tradingQueue.reversed()

		val game = service.reorderQueue("game-1", "creator@example.com", reversed)

		assertThat(game.turn!!.tradingQueue).isEqualTo(reversed)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldRefuseAnOrderThatWentStaleAndLeaveTheGameAsItWas() {
		gameStore.create(trading)
		val staleOrder = trading.turn!!.tradingQueue.reversed()
		val resolved = service.resolveNext("game-1", "creator@example.com")

		assertThatThrownBy { service.reorderQueue("game-1", "creator@example.com", staleOrder) }
			.isInstanceOf(InvalidGameOperationException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(resolved)
	}

	@Test
	fun shouldRefuseAResolutionOutOfTurnAndLeaveTheGameAsItWas() {
		gameStore.create(trading)

		assertThatThrownBy { service.resolveNext("game-1", "joiner@example.com") }.isInstanceOf(NotYourTurnException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(trading)
	}
}
