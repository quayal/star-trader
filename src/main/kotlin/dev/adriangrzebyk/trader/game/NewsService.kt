package dev.adriangrzebyk.trader.game

/**
 * The News phase's actions (R109, R110): learning a tip-off and signalling done. Each runs inside [GameStore.update], so
 * it's checked against the saved game and a double press applies once (A04).
 */
class NewsService(private val gameStore: GameStore) {

	/** [callerEmail] is done with the news (R109); once everyone is, Jumps starts. */
	fun finish(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.signalDoneNews(callerEmail) }
}
