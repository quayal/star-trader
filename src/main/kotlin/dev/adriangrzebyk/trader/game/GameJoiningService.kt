package dev.adriangrzebyk.trader.game

class GameJoiningService(private val gameStore: GameStore) {

	/** Adds [player] through [GameStore.update], so two players joining at once can't overwrite each other. */
	fun joinGame(gameId: String, player: Player): Game = gameStore.update(gameId) { it.addPlayer(player) }
}
