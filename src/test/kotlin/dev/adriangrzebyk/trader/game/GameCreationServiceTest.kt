package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameCreationServiceTest {

	private val gameStore = FakeGameStore()
	private val service = GameCreationService(FakeGameIdGenerator("game-1"), gameStore)
	private val creator = Player.newcomer("creator@example.com", "Creator", "Creator Corp")

	@Test
	fun shouldCreateAGameInTheLobbyWithTheCreatorAsItsOnlyPlayer() {
		val game = service.createGame(creator, 20)

		assertThat(game).isEqualTo(Game("game-1", 20, GameStatus.LOBBY, "creator@example.com", listOf(creator), null, emptyList()))
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldStoreNothingWhenTheLengthIsRejected() {
		// The rule itself is Game.lobby's (GameTest); here only that a refused game isn't stored.
		assertThatThrownBy { service.createGame(creator, 51) }.isInstanceOf(InvalidGameOperationException::class.java)
		assertThat(gameStore.games).isEmpty()
	}
}
