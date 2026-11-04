package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem

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

	/**
	 * Places [callerEmail]'s secret offer inside [GameStore.update], so an offer that arrives as the last player's
	 * "done" closes Bidding is either placed before the close or refused after it, never lost silently (A04).
	 */
	fun placeOffer(gameId: String, callerEmail: String, system: StarSystem, goods: Goods, type: OfferType, price: Int?): Game {
		gameStore.find(gameId) ?: throw GameNotFoundException()
		return gameStore.update(gameId) { it.placeOffer(callerEmail, system, goods, type, price) }
	}
}
