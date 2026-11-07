package dev.adriangrzebyk.trader.game

/**
 * How one good at one system resolves in Trading (R34). Public: once a good starts resolving, its [offers] are disclosed
 * to every player (E05-S02-1), in the order they were placed, with the [dictator]'s own offer last if they place one
 * (R38). [dictator] is the email of that good's Dictator at that system, or null when it has none; they're asked once,
 * before the roll, and [dictatorDecided] records that they answered.
 */
data class Resolution(val good: MarketGood, val offers: List<Offer>, val dictator: String?, val dictatorDecided: Boolean) {

	/** Whom the resolution waits for to place an offer or pass (R38, D09): the Dictator until they decide, else null. */
	fun awaitingDictator(): String? = dictator?.takeUnless { dictatorDecided }

	/**
	 * Whether nothing is left to decide. The counter and the trades (E05-S03 on) follow the Dictator's step; until they
	 * exist, the resolution finishes there.
	 */
	fun isFinished(): Boolean = awaitingDictator() == null

	/** The Dictator's offer, or null when there is no Dictator, they haven't decided yet, or they passed. */
	fun dictatorOffer(): Offer? = dictator?.let { email -> offers.firstOrNull { it.playerEmail == email } }

	/** This resolution after the Dictator placed [offer], which is then disclosed like the others (S02-3). */
	fun withDictatorOffer(offer: Offer): Resolution {
		requireDictatorToDecide()
		return copy(offers = offers + offer, dictatorDecided = true)
	}

	/** This resolution after the Dictator passed (S02-2). */
	fun withDictatorPassed(): Resolution {
		requireDictatorToDecide()
		return copy(dictatorDecided = true)
	}

	private fun requireDictatorToDecide() {
		if (awaitingDictator() == null) {
			throw InvalidGameOperationException("No Dictator decision is pending.")
		}
	}

	companion object {
		/**
		 * [good] starting to resolve: of [turnOffers], those for [good] are disclosed (S02-1), and [dictator], if any, is
		 * asked before the roll (S02-2, S02-4).
		 */
		fun start(good: MarketGood, turnOffers: List<Offer>, dictator: String?) =
			Resolution(good, turnOffers.filter { it.isFor(good) }, dictator, false)
	}
}
