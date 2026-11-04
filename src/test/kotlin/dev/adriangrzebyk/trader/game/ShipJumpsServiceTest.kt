package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ShipJumpsServiceTest {

	private val gameStore = FakeGameStore()
	private val service = ShipJumpsService(gameStore)
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")

	// Ada rolls 2, Grace 1: Ada picks first.
	private val shipJumps = Game.lobby("game-1", 20, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))
		.advancePhase(FakeDice(2, 1))

	@Test
	fun shouldStoreThePick() {
		gameStore.create(shipJumps)

		val game = service.pickPlace("game-1", "creator@example.com", 2)

		assertThat(game.turn!!.turnOrder()).containsExactly("joiner@example.com", "creator@example.com")
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldRefuseAPickOutOfTurnAndLeaveTheGameAsItWas() {
		gameStore.create(shipJumps)

		assertThatThrownBy { service.pickPlace("game-1", "joiner@example.com", 1) }.isInstanceOf(NotYourTurnException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(shipJumps)
	}

	@Test
	fun shouldRejectAnUnknownGame() {
		assertThatThrownBy { service.pickPlace("missing", "creator@example.com", 1) }.isInstanceOf(GameNotFoundException::class.java)
	}
}
