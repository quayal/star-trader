package dev.adriangrzebyk.trader.game

class GameStartingService(private val gameStore: GameStore, private val dice: Dice) {

	/**
	 * Starts the game inside [GameStore.update], so a join arriving at the same moment is either in or refused. The
	 * starting connection points are rolled with [dice] (R82); a replayed change rolls again, and only one roll is saved.
	 */
	fun startGame(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.start(callerEmail, dice) }
}
