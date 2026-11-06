package dev.adriangrzebyk.trader.game

class GameStartingService(private val gameStore: GameStore, private val picker: StartingSystemPicker) {

	/** Starts the game inside [GameStore.update], so a join arriving at the same moment is either in or refused. */
	fun startGame(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.start(callerEmail, picker) }
}
