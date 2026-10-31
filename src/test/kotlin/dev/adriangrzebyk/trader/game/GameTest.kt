package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameTest {

	private val creator = Player("creator@example.com", "Creator", "Creator Corp")

	@Test
	fun shouldAddAPlayerToTheGame() {
		val game = gameWith(creator)
		val joiner = Player("joiner@example.com", "Joiner", "Joiner Corp")

		val updated = game.addPlayer(joiner)

		assertThat(updated.players).containsExactly(creator, joiner)
	}

	@Test
	fun shouldRejectASeventhPlayer() {
		val game = gameWith(*(1..6).map { Player("p$it@example.com", "P$it", "Corp $it") }.toTypedArray())

		assertThatThrownBy { game.addPlayer(Player("p7@example.com", "P7", "Corp 7")) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game is full")
	}

	@Test
	fun shouldRejectAPlayerWhoIsAlreadyInTheGame() {
		val game = gameWith(creator)

		assertThatThrownBy { game.addPlayer(creator.copy(name = "Someone else")) }
			.isInstanceOf(InvalidGameOperationException::class.java)
	}

	@Test
	fun shouldRejectAPlayerOnceTheGameHasStarted() {
		val game = gameWith(creator).copy(status = GameStatus.RUNNING)

		assertThatThrownBy { game.addPlayer(Player("late@example.com", "Late", "Late Corp")) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game has already started")
	}

	private fun gameWith(vararg players: Player) = Game("game-1", 20, GameStatus.LOBBY, players.first().email, players.toList())
}
