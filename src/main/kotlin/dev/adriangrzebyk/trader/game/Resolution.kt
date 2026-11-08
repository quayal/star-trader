package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/**
 * How one good at one system resolves in Trading (R34). Public: once a good starts resolving, its [offers] are disclosed
 * to every player (E05-S02-1), in the order they were placed, with the [dictator]'s own offer last if they place one
 * (R38). [dictator] is the email of that good's Dictator at that system, or null when it has none; they're asked once,
 * before the roll, and [dictatorDecided] records that they answered. [startingCounter] is the good's S/D plus the 2d6
 * roll, null until the counter starts once nobody is asked (S03-1). [trades] are the offers the counter reached, in
 * order, each with what its player chose to trade (E05-S04). [initiative] is the turn's initiative order, which breaks
 * ties (R34); empty on a resolution saved before E05-S04, which had run to its end.
 */
data class Resolution(
	val good: MarketGood,
	val offers: List<Offer>,
	val dictator: String?,
	val dictatorDecided: Boolean,
	val startingCounter: Int?,
	val trades: List<Trade>,
	val initiative: List<String>
) {

	/** Whom the resolution waits for to place an offer or pass (R38, D09): the Dictator until they decide, else null. */
	fun awaitingDictator(): String? = dictator?.takeUnless { dictatorDecided }

	/**
	 * The trade the resolution waits for its player to choose an amount for (E05-S04-1, D09): the next offer the counter
	 * reaches, with [Trade.units] the most the counter allows (before that player's money and cargo, R12, R10). Null
	 * before the counter starts and once no offer that hasn't traded allows a unit (S03-3).
	 */
	fun awaitingTrade(): Trade? = startingCounter?.let { nextTrade() }

	/** Whether nothing is left to decide: the Dictator has decided, and no offer can trade any more. */
	fun isFinished(): Boolean = awaitingDictator() == null && awaitingTrade() == null

	/** The Dictator's offer, or null when there is no Dictator, they haven't decided yet, or they passed. */
	fun dictatorOffer(): Offer? = dictator?.let { email -> offers.firstOrNull { it.playerEmail == email } }

	/** The counter now (R34): where it started, moved by every trade so far; null before it starts. */
	fun counter(): Int? = startingCounter?.let { start -> trades.fold(start) { counter, trade -> trade.counterAfter(counter) } }

	/**
	 * The counter in plain terms once it has started (S03-6, D07: no dice arithmetic): "market +3 → buyers first", or
	 * "market −2 → sellers first" (R34: 0 or more favours buyers). Null before it starts.
	 */
	fun marketText(): String? = startingCounter?.let { counter ->
		val signed = if (counter > 0) "+$counter" else counter.toString().replace("-", "\u2212")
		"market $signed → ${if (counter >= 0) "buyers" else "sellers"} first"
	}

	/**
	 * How the counter ended (S03-6, S03-7): "No one else can trade" after a trade, "No one can trade" when no unit traded.
	 */
	fun endText(): String = if (trades.none { it.tradedAny() }) "No one can trade" else "No one else can trade"

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

	/**
	 * This resolution with its counter started, once nobody is asked any more: at the good's S/D plus a 2d6 roll from
	 * [dice] (S03-1), so the roll never exists while the Dictator decides (R38). Then it waits for each trader in turn
	 * ([awaitingTrade]). Unchanged while the Dictator is asked or once started.
	 */
	fun startCounterOnceDecided(dice: Dice): Resolution {
		if (awaitingDictator() != null || startingCounter != null) {
			return this
		}
		return copy(startingCounter = good.supplyDemand() + dice.twoD6())
	}

	/**
	 * This resolution after the awaited trade's player traded [trade]'s units (E05-S04): from 0, when they could trade
	 * none (S04-5), to the most the counter allows. Then the counter has moved, and the next offer it reaches is awaited.
	 */
	fun withTrade(trade: Trade): Resolution {
		val awaited = awaitingTrade() ?: throw InvalidGameOperationException("No trade is waiting for a decision.")
		check(trade.offer == awaited.offer && trade.units in 0..awaited.units) { "$trade isn't the awaited $awaited" }
		return copy(trades = trades + trade)
	}

	/**
	 * Who trades next and how much at most (R34), once the counter has started, or null when no offer that hasn't traded
	 * allows a unit (S03-3). The first trade is the winning offer, the highest buy if the counter is 0 or more, else the
	 * lowest sell (S03-2); when that side has no offer able to trade, it goes as every later one does: the most units at
	 * the current counter, a buyer–seller tie by the counter's sign (0 or more: the buyer), then [initiative] order.
	 */
	private fun nextTrade(): Trade? {
		val counter = checkNotNull(counter())
		val able = offers.filter { offer -> trades.none { it.offer == offer } && maxUnitsOf(offer) >= 1 }
		val favoured = if (counter >= 0) OfferType.BUY else OfferType.SELL
		val byInitiative = compareBy<Offer> { offer -> initiative.indexOf(offer.playerEmail).takeIf { it >= 0 } ?: Int.MAX_VALUE }
		val winners = if (trades.isEmpty()) able.filter { it.type == favoured } else emptyList()
		val winner = winners.sortedWith(compareBy(Offer::bestPriceFirst).then(byInitiative)).firstOrNull()
		val mostUnits = able.sortedWith(compareByDescending(::maxUnitsOf).thenBy { it.type != favoured }.then(byInitiative)).firstOrNull()
		return (winner ?: mostUnits)?.let { Trade(it, maxUnitsOf(it)) }
	}

	/** The most units [offer] allows at the counter now, from the start price (R34). */
	private fun maxUnitsOf(offer: Offer): Int = offer.maxUnits(checkNotNull(counter()), good.startPrice())

	private fun requireDictatorToDecide() {
		if (awaitingDictator() == null) {
			throw InvalidGameOperationException("No Dictator decision is pending.")
		}
	}

	companion object {
		/**
		 * [good] starting to resolve: of [turnOffers], those for [good] are disclosed (S02-1), and [dictator], if any, is
		 * asked before the roll (S02-2, S02-4). [initiative] is the turn's, for ties (R34).
		 */
		fun start(good: MarketGood, turnOffers: List<Offer>, dictator: String?, initiative: List<String>) =
			Resolution(good, turnOffers.filter { it.isFor(good) }, dictator, false, null, emptyList(), initiative)
	}
}
