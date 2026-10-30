package dev.adriangrzebyk.trader.game.adapter.memory

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.Player
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryGameStoreTest {

	private val store = InMemoryGameStore()
	private val creator = Player("creator@example.com", "Creator", "Creator Corp")
	private val game = Game("game-1", 20, GameStatus.LOBBY, creator.email, listOf(creator))

	@Test
	fun shouldFindACreatedGameById() {
		store.create(game)

		assertThat(store.find("game-1")).isEqualTo(game)
		assertThat(store.find("game-2")).isNull()
	}

	@Test
	fun shouldKeepGamesSeparateById() {
		val other = game.copy(id = "game-2", turns = 5)
		store.create(game)
		store.create(other)

		assertThat(store.find("game-1")).isEqualTo(game)
		assertThat(store.find("game-2")).isEqualTo(other)
	}

	@Test
	fun shouldRejectASecondGameWithTheSameId() {
		store.create(game)

		assertThatThrownBy { store.create(game.copy(turns = 5)) }.isInstanceOf(IllegalStateException::class.java)
		assertThat(store.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldUpdateAGame() {
		store.create(game)
		val joiner = Player("joiner@example.com", "Joiner", "Joiner Corp")

		val updated = store.update("game-1") { it.addPlayer(joiner) }

		assertThat(updated.players).containsExactly(creator, joiner)
		assertThat(store.find("game-1")).isEqualTo(updated)
	}

	@Test
	fun shouldFailToUpdateAnUnknownGame() {
		assertThatThrownBy { store.update("missing") { it } }.isInstanceOf(NoSuchElementException::class.java)
	}

	@Test
	fun shouldApplyConcurrentUpdatesOneAtATime() {
		store.create(game)
		val executor = Executors.newFixedThreadPool(8)

		(1..5).forEach { n ->
			executor.submit { store.update("game-1") { it.addPlayer(Player("p$n@example.com", "P$n", "Corp $n")) } }
		}
		executor.shutdown()
		executor.awaitTermination(5, TimeUnit.SECONDS)

		assertThat(store.find("game-1")!!.players).hasSize(6)
	}
}
