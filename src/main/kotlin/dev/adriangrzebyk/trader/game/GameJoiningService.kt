package dev.adriangrzebyk.trader.game

/**
 * Joining a game (R120): a player asks, and the game's creator accepts or declines. Each step goes through
 * [GameStore.update], so two players asking at once, or the creator deciding while a player withdraws, can't overwrite
 * each other.
 */
class GameJoiningService(private val gameStore: GameStore) {

	fun requestToJoin(gameId: String, player: Player): Game = gameStore.update(gameId) { it.requestToJoin(player) }

	fun withdrawJoinRequest(gameId: String, email: String): Game = gameStore.update(gameId) { it.withdrawJoinRequest(email) }

	fun acceptJoinRequest(gameId: String, callerEmail: String, email: String): Game =
		gameStore.update(gameId) { it.acceptJoinRequest(callerEmail, email) }

	fun declineJoinRequest(gameId: String, callerEmail: String, email: String): Game =
		gameStore.update(gameId) { it.declineJoinRequest(callerEmail, email) }
}
