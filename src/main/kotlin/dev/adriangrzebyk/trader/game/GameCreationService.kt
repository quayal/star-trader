package dev.adriangrzebyk.trader.game

class GameCreationService(private val idGenerator: GameIdGenerator, private val gameStore: GameStore) {

	/** Opens a new game's lobby with [creator] in it; [Game.lobby] checks the number of [turns]. */
	fun createGame(creator: Player, turns: Int?): Game {
		val game = Game.lobby(idGenerator.generate(), turns, creator)
		gameStore.create(game)
		return game
	}
}
