package dev.adriangrzebyk.trader.game

class GameStartingService(private val gameStore: GameStore, private val picker: StartingSystemPicker) {

	/** Starts the game inside [GameStore.update], so a join arriving at the same moment is either in or refused. */
	fun startGame(gameId: String, callerEmail: String): Game {
		// Games are never deleted, so one found here is still there for the update.
		gameStore.find(gameId) ?: throw GameNotFoundException()
		return gameStore.update(gameId) { it.start(callerEmail, picker) }
	}
}
