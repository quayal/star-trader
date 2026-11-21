package dev.adriangrzebyk.trader.game

class OpenGamesService(private val gameStore: GameStore) {

	/** R119: every game still in its lobby, the ones [viewerEmail] is in first, then the ones they asked to join. */
	fun openGames(viewerEmail: String): List<Game> = gameStore.lobbies().sortedWith(compareBy({ it.lobbyStandingOf(viewerEmail) }, { it.id }))
}
