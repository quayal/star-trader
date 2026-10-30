package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GameCreationServiceTest {

	private val gameStore = FakeGameStore()
	private val service = GameCreationService(FakeGameIdGenerator("game-1"), gameStore)
	private val creator = Player("creator@example.com", "Creator", "Creator Corp")

	@Test
	fun shouldCreateAGameInTheLobbyWithTheCreatorAsItsOnlyPlayer() {
		val game = service.createGame(creator, 20)

		assertThat(game).isEqualTo(Game("game-1", 20, GameStatus.LOBBY, "creator@example.com", listOf(creator)))
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@ParameterizedTest
	@ValueSource(ints = [1, 50])
	fun shouldAcceptTheBoundaryLengths(turns: Int) {
		assertThat(service.createGame(creator, turns).turns).isEqualTo(turns)
	}

	@ParameterizedTest
	@ValueSource(ints = [0, 51, -3])
	fun shouldRejectALengthOutsideOneToFifty(turns: Int) {
		assertThatThrownBy { service.createGame(creator, turns) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Enter a whole number of turns from 1 to 50.")
		assertThat(gameStore.games).isEmpty()
	}

	@Test
	fun shouldRejectAMissingLength() {
		assertThatThrownBy { service.createGame(creator, null) }
			.isInstanceOf(InvalidGameOperationException::class.java)
	}
}
