package dev.adriangrzebyk.trader.game

class GameJoiningService(private val gameStore: GameStore) {

	/** Adds [player] through [GameStore.update], so two players joining at once can't overwrite each other. */
	fun joinGame(gameId: String, player: Player): Game {
		// Games are never deleted, so one found here is still there for the update.
		gameStore.find(gameId) ?: throw GameNotFoundException()
		return gameStore.update(gameId) { it.addPlayer(player) }
	}
}
