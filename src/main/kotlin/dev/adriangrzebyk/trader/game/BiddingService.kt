package dev.adriangrzebyk.trader.game

class BiddingService(private val gameStore: GameStore) {

	/**
	 * Records [callerEmail] as done bidding inside [GameStore.update], so when the last two players signal at
	 * the same moment Bidding closes exactly once, and a signal that arrives after the close is refused (A04).
	 */
	fun signalDone(gameId: String, callerEmail: String): Game {
		// Games are never deleted, so one found here is still there for the update.
		gameStore.find(gameId) ?: throw GameNotFoundException()
		return gameStore.update(gameId) { it.signalDoneBidding(callerEmail) }
	}
}
