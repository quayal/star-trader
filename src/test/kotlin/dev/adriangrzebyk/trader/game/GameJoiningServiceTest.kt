package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameJoiningServiceTest {

	private val gameStore = FakeGameStore()
	private val service = GameJoiningService(gameStore)
	private val creator = Player.newcomer("creator@example.com", "Creator", "Creator Corp")
	private val joiner = Player.newcomer("joiner@example.com", "Joiner", "Joiner Corp")
	private val game = Game("game-1", 20, GameStatus.LOBBY, creator.email, listOf(creator), null, emptyList())

	@Test
	fun shouldAddThePlayerToTheGame() {
		gameStore.create(game)

		val joined = service.joinGame("game-1", joiner)

		assertThat(joined.players).containsExactly(creator, joiner)
		assertThat(gameStore.find("game-1")).isEqualTo(joined)
	}

	@Test
	fun shouldRejectAnUnknownGame() {
		assertThatThrownBy { service.joinGame("missing", joiner) }
			.isInstanceOf(GameNotFoundException::class.java)
			.hasMessage("This game doesn't exist")
	}

	@Test
	fun shouldLeaveTheGameUnchangedWhenThePlayerCannotJoin() {
		gameStore.create(game)

		assertThatThrownBy { service.joinGame("game-1", creator) }.isInstanceOf(InvalidGameOperationException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}
}
