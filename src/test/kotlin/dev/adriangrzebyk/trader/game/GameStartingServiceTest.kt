package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameStartingServiceTest {

	private val gameStore = FakeGameStore()
	private val picker = FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS)
	private val service = GameStartingService(gameStore, picker)
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val lobby = Game.lobby("game-1", 20, creator).addPlayer(joiner)

	@Test
	fun shouldStartTheGameAndStoreIt() {
		gameStore.create(lobby)

		val started = service.startGame("game-1", "creator@example.com")

		assertThat(started.status).isEqualTo(GameStatus.RUNNING)
		assertThat(gameStore.find("game-1")).isEqualTo(started)
	}

	@Test
	fun shouldRejectAnUnknownGame() {
		assertThatThrownBy { service.startGame("missing", "creator@example.com") }.isInstanceOf(GameNotFoundException::class.java)
	}

	@Test
	fun shouldLeaveTheGameInTheLobbyWhenSomeoneElseTriesToStartIt() {
		gameStore.create(lobby)

		assertThatThrownBy { service.startGame("game-1", "joiner@example.com") }.isInstanceOf(NotTheCreatorException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(lobby)
	}
}
