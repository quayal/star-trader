package dev.adriangrzebyk.trader.game

class GamePollingService(private val gameStore: GameStore) {

	/** The game's current state, for one of its players only; anyone else gets [NotAGameMemberException] (A05). */
	fun poll(gameId: String, callerEmail: String): Game {
		val game = gameStore.find(gameId) ?: throw GameNotFoundException()
		if (!game.hasPlayer(callerEmail)) {
			throw NotAGameMemberException()
		}
		return game
	}
}
