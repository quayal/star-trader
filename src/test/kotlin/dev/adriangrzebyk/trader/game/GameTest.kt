package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameTest {

	private val creator = Player.newcomer("creator@example.com", "Creator", "Creator Corp")

	@Test
	fun shouldAddAPlayerToTheGame() {
		val game = gameWith(creator)
		val joiner = Player.newcomer("joiner@example.com", "Joiner", "Joiner Corp")

		val updated = game.addPlayer(joiner)

		assertThat(updated.players).containsExactly(creator, joiner)
	}

	@Test
	fun shouldRejectASeventhPlayer() {
		val game = gameWith(*(1..6).map { Player.newcomer("p$it@example.com", "P$it", "Corp $it") }.toTypedArray())

		assertThatThrownBy { game.addPlayer(Player.newcomer("p7@example.com", "P7", "Corp 7")) }
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

		assertThatThrownBy { game.addPlayer(Player.newcomer("late@example.com", "Late", "Late Corp")) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game has already started")
	}

	@Test
	fun shouldKnowWhoItsPlayersAre() {
		val game = gameWith(creator)

		assertThat(game.hasPlayer("creator@example.com")).isTrue()
		assertThat(game.hasPlayer("outsider@example.com")).isFalse()
	}

	private fun gameWith(vararg players: Player) = Game("game-1", 20, GameStatus.LOBBY, players.first().email, players.toList(), null, emptyList())
}
