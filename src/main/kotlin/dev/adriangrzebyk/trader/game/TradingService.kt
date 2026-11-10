package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/** Trading's actions, each applied inside [GameStore.update]; [dice] rolls each resolution's counter (R34) and
 * each position roll after a trade of 6 or more units (R43). */
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

	/**
	 * The trader whose offer the counter reached trades [amount] units split as [loads] (E05-S04), inside
	 * [GameStore.update], so a double press trades once and is checked against the saved resolution (A04).
	 */
	fun trade(gameId: String, callerEmail: String, amount: Int?, loads: List<ShipLoad>): Game =
		gameStore.update(gameId) { it.trade(callerEmail, amount, loads, dice) }

	/** The trader whose offer the counter reached trades nothing, inside [GameStore.update] (E05-S04-5, A04). */
	fun tradeNothing(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.tradeNothing(callerEmail) }

	/**
	 * The player asked in the wholesale step buys or sells [amount] units at the new price, split as [loads] (R37,
	 * E05-S05-4), inside [GameStore.update], so a double press trades once (A04).
	 */
	fun wholesale(gameId: String, callerEmail: String, type: OfferType?, amount: Int?, loads: List<ShipLoad>): Game =
		gameStore.update(gameId) { it.wholesale(callerEmail, type, amount, loads) }

	/** The player asked in the wholesale step passes, inside [GameStore.update] (S05-4, A04). */
	fun passWholesale(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.passWholesale(callerEmail) }
}
