package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GamePollingServiceTest {

	private val gameStore = FakeGameStore()
	private val service = GamePollingService(gameStore)
	private val creator = Player("creator@example.com", "Creator", "Creator Corp")
	private val joiner = Player("joiner@example.com", "Joiner", "Joiner Corp")
	private val game = Game("game-1", 20, GameStatus.LOBBY, creator.email, listOf(creator, joiner))

	@Test
	fun shouldReturnTheGameToItsCreator() {
		gameStore.create(game)

		assertThat(service.poll("game-1", creator.email)).isEqualTo(game)
	}

	@Test
	fun shouldReturnTheGameToAPlayerWhoJoinedIt() {
		gameStore.create(game)

		assertThat(service.poll("game-1", joiner.email)).isEqualTo(game)
	}

	@Test
	fun shouldRejectSomeoneWhoIsNotAPlayerInTheGame() {
		gameStore.create(game)

		assertThatThrownBy { service.poll("game-1", "outsider@example.com") }
			.isInstanceOf(NotAGameMemberException::class.java)
			.hasMessage("You are not a player in this game")
	}

	@Test
	fun shouldRejectAnUnknownGame() {
		assertThatThrownBy { service.poll("missing", creator.email) }.isInstanceOf(GameNotFoundException::class.java)
	}
}
