package dev.adriangrzebyk.trader.game

class GameCreationService(private val idGenerator: GameIdGenerator, private val gameStore: GameStore,) {

	fun createGame(creator: Player, turns: Int?): Game {
		if (turns == null || turns !in MIN_TURNS..MAX_TURNS) {
			throw InvalidGameOperationException("Enter a whole number of turns from $MIN_TURNS to $MAX_TURNS.")
		}

		val game = Game(idGenerator.generate(), turns, GameStatus.LOBBY, creator.email, listOf(creator))
		gameStore.create(game)
		return game
	}

	companion object {
		const val MIN_TURNS = 1
		const val MAX_TURNS = 50
	}
}
