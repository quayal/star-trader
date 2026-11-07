package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/** Trading's actions, each applied inside [GameStore.update]; [dice] rolls each resolution's counter (R34). */
class TradingService(private val gameStore: GameStore, private val dice: Dice) {

	/**
	 * [callerEmail] puts the goods still to resolve in [order] inside [GameStore.update], so an order sent while a good
	 * resolved is checked against the saved queue and refused, not applied over it (A04, R25).
	 */
	fun reorderQueue(gameId: String, callerEmail: String, order: List<MarketGood>): Game =
		gameStore.update(gameId) { it.reorderTradingQueue(callerEmail, order) }

	/** [callerEmail] resolves the next good inside [GameStore.update], so a double press resolves one good, not two (A04, R25). */
	fun resolveNext(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.resolveNextGood(callerEmail, dice) }

	/** The Dictator places their offer inside [GameStore.update], so it's checked against the saved resolution (A04, R38). */
	fun placeDictatorOffer(gameId: String, callerEmail: String, type: OfferType, price: Int?): Game =
		gameStore.update(gameId) { it.placeDictatorOffer(callerEmail, type, price, dice) }

	/** The Dictator passes inside [GameStore.update], so a pass and an offer sent together can't both apply (A04, R38). */
	fun passAsDictator(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.passAsDictator(callerEmail, dice) }
}
