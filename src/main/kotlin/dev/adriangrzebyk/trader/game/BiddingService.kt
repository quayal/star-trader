package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem

class BiddingService(private val gameStore: GameStore, private val dice: Dice) {

	/**
	 * Records [callerEmail] as done bidding inside [GameStore.update], so when the last two players signal at
	 * the same moment Bidding closes exactly once, and a signal that arrives after the close is refused (A04). The
	 * close charges the bids and rolls the initiative order (R27); if the store runs the change again after a
	 * conflicting write, it rolls again, and only one roll is ever saved.
	 */
	fun signalDone(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.signalDoneBidding(callerEmail, dice) }

	/**
	 * Places [callerEmail]'s secret offer inside [GameStore.update], so an offer that arrives as the last player's
	 * "done" closes Bidding is either placed before the close or refused after it, never lost silently (A04).
	 */
	fun placeOffer(gameId: String, callerEmail: String, system: StarSystem, goods: Goods, type: OfferType, price: Int?): Game =
		gameStore.update(gameId) { it.placeOffer(callerEmail, system, goods, type, price) }

	/**
	 * Places or changes [callerEmail]'s secret initiative bid inside [GameStore.update], so a bid that arrives as the
	 * last "done" closes Bidding is either counted in the close or refused after it, never lost silently (A04).
	 */
	fun placeInitiativeBid(gameId: String, callerEmail: String, amount: Int?): Game =
		gameStore.update(gameId) { it.placeInitiativeBid(callerEmail, amount) }
}
