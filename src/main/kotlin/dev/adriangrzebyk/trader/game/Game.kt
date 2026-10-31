package dev.adriangrzebyk.trader.game

/** [turn] is null until the game starts; [log] holds the public events, oldest first. */
data class Game(
	val id: String,
	val turns: Int,
	val status: GameStatus,
	val creatorEmail: String,
	val players: List<Player>,
	val turn: Turn?,
	val log: List<String>,
) {

	fun addPlayer(player: Player): Game {
		if (status != GameStatus.LOBBY) {
			throw InvalidGameOperationException("This game has already started")
		}
		if (players.size >= MAX_PLAYERS) {
			throw InvalidGameOperationException("This game is full")
		}
		if (hasPlayer(player.email)) {
			throw InvalidGameOperationException("This player is already in the game")
		}
		return copy(players = players + player)
	}

	fun hasPlayer(email: String): Boolean = players.any { it.email == email }

	companion object {
		const val MAX_PLAYERS = 6

		/** A new game waiting in the lobby with its creator as the only player. */
		fun lobby(id: String, turns: Int, creator: Player) = Game(id, turns, GameStatus.LOBBY, creator.email, listOf(creator), null, emptyList())
	}
}
