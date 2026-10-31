package dev.adriangrzebyk.trader.game.adapter.memory

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStore
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryGameStore : GameStore {

	private val gamesById = ConcurrentHashMap<String, Game>()

	override fun create(game: Game) {
		check(gamesById.putIfAbsent(game.id, game) == null) { "A game with id ${game.id} already exists" }
	}

	override fun find(id: String): Game? = gamesById[id]

	// computeIfPresent holds the entry's lock while mutate runs, so concurrent updates to one game never interleave.
	override fun update(id: String, mutate: (Game) -> Game): Game =
		gamesById.computeIfPresent(id) { _, game -> mutate(game) } ?: throw NoSuchElementException("No game with id $id")
}
