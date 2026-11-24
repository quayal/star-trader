package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem

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
	 * Withdraws [callerEmail]'s offer on [goods] at [system] inside [GameStore.update], so a withdrawal that arrives as
	 * Bidding closes is either applied before the close or refused after it (A04, R59).
	 */
	fun withdrawOffer(gameId: String, callerEmail: String, system: StarSystem, goods: Goods): Game =
		gameStore.update(gameId) { it.withdrawOffer(callerEmail, system, goods) }

	/**
	 * Places [callerEmail]'s starting connection points inside [GameStore.update] (R82), so a double press places them once
	 * and a placement that arrives as Bidding closes is either in or refused.
	 */
	fun placeConnections(gameId: String, callerEmail: String, political: Int?, business: Int?, criminal: Int?): Game =
		gameStore.update(gameId) { it.placeConnections(callerEmail, political, business, criminal) }

	/**
	 * Places [callerEmail]'s starting ship inside [GameStore.update] (R132), so a double press places one and a placement that
	 * arrives as Bidding closes is either in or refused.
	 */
	fun placeStartingShip(gameId: String, callerEmail: String, name: String?, system: StarSystem?): Game =
		gameStore.update(gameId) { it.placeStartingShip(callerEmail, name, system) }

	/**
	 * Places or changes [callerEmail]'s secret initiative bid inside [GameStore.update], so a bid that arrives as the
	 * last "done" closes Bidding is either counted in the close or refused after it, never lost silently (A04).
	 */
	fun placeInitiativeBid(gameId: String, callerEmail: String, amount: Int?): Game =
		gameStore.update(gameId) { it.placeInitiativeBid(callerEmail, amount) }
}
