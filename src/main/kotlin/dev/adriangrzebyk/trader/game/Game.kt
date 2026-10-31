package dev.adriangrzebyk.trader.game

data class Game(val id: String, val turns: Int, val status: GameStatus, val creatorEmail: String, val players: List<Player>,) {

	fun addPlayer(player: Player): Game {
		if (status != GameStatus.LOBBY) {
			throw InvalidGameOperationException("This game has already started")
		}
		if (players.size >= MAX_PLAYERS) {
			throw InvalidGameOperationException("This game is full")
		}
		if (players.any { it.email == player.email }) {
			throw InvalidGameOperationException("This player is already in the game")
		}
		return copy(players = players + player)
	}

	companion object {
		const val MAX_PLAYERS = 6
	}
}
