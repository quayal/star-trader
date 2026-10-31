package dev.adriangrzebyk.trader.game

interface GameStore {

	fun create(game: Game)

	fun find(id: String): Game?

	/**
	 * Reads, changes and writes one game as a single atomic step; throws if no game has [id].
	 * A store may run [mutate] more than once when another change lands first, so it must have no side effects.
	 */
	fun update(id: String, mutate: (Game) -> Game): Game
}
