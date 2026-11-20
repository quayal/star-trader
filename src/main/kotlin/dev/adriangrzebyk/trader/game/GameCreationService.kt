package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.news.News

class GameCreationService(private val idGenerator: GameIdGenerator, private val gameStore: GameStore) {

	/** Opens a new game's lobby with [creator] in it; [Game.lobby] checks the number of [turns]. */
	fun createGame(creator: Player, turns: Int?): Game {
		val game = Game.lobby(idGenerator.generate(), turns, creator, News.full())
		gameStore.create(game)
		return game
	}
}
