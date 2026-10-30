package dev.adriangrzebyk.trader.game

class FakeGameStore : GameStore {

	val games = mutableMapOf<String, Game>()

	override fun create(game: Game) {
		check(game.id !in games) { "A game with id ${game.id} already exists" }
		games[game.id] = game
	}

	override fun find(id: String): Game? = games[id]

	override fun update(id: String, mutate: (Game) -> Game): Game {
		val updated = mutate(games[id] ?: throw NoSuchElementException("No game $id"))
		games[id] = updated
		return updated
	}
}
