package dev.adriangrzebyk.trader.game

class TradingService(private val gameStore: GameStore) {

	/**
	 * [callerEmail] puts the goods still to resolve in [order] inside [GameStore.update], so an order sent while a good
	 * resolved is checked against the saved queue and refused, not applied over it (A04, R25).
	 */
	fun reorderQueue(gameId: String, callerEmail: String, order: List<MarketGood>): Game =
		gameStore.update(gameId) { it.reorderTradingQueue(callerEmail, order) }

	/** [callerEmail] resolves the next good inside [GameStore.update], so a double press resolves one good, not two (A04, R25). */
	fun resolveNext(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.resolveNextGood(callerEmail) }
}
