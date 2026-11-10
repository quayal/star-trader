package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.SupplyDemand

/**
 * How one good at one system resolves in Trading (R34). Public: once a good starts resolving, its [offers] are disclosed
 * to every player (E05-S02-1), in the order they were placed, with the [dictator]'s own offer last if they place one
 * (R38). [dictator] is the email of that good's Dictator at that system, or null when it has none; they're asked once,
 * before the roll, and [dictatorDecided] records that they answered. [startingCounter] is the good's S/D plus the 2d6
 * roll, null until the counter starts once nobody is asked (S03-1). [trades] are the offers the counter reached, in
 * order, each with what its player chose to trade (E05-S04). [initiative] is the turn's initiative order, which breaks
 * ties (R34); empty on a resolution saved before E05-S04, which had run to its end. [startPrice] is the good's price
 * when it started resolving: every max uses it (R34), and the price changes once, from it, at the end (R35).
 * [wholesalers] are who the wholesale step asks once no offer can trade, in order (R37, E05-S05-4), and [wholesales]
 * their answers so far.
 */
data class Resolution(
	val good: MarketGood,
	val startPrice: Int,
	val offers: List<Offer>,
	val dictator: String?,
	val dictatorDecided: Boolean,
	val startingCounter: Int?,
	val trades: List<Trade>,
	val initiative: List<String>,
	val wholesalers: List<String>,
	val wholesales: List<Wholesale>
) {

	/** Whom the resolution waits for to place an offer or pass (R38, D09): the Dictator until they decide, else null. */
	fun awaitingDictator(): String? = dictator?.takeUnless { dictatorDecided }

	/**
	 * The trade the resolution waits for its player to choose an amount for (E05-S04-1, D09): the next offer the counter
	 * reaches, with the most the market allows. Null before the counter starts and once no offer that hasn't traded allows
	 * a unit (S03-3).
	 */
	fun awaitingTrade(): AwaitedTrade? = startingCounter?.let { nextTrade() }

	/** Whether the counter has started and no offer can trade any more (S03-3): the price then changes (R35). */
	fun isTradingOver(): Boolean = startingCounter != null && awaitingTrade() == null

	/**
	 * The good's new price once trading is over (E05-S05-1, R35): the start price plus the price modifier of the counter's
	 * final position, clamped to 1–20 HT; with no trades the roll alone moves it. Null until then.
	 */
	fun newPrice(): Int? = counter()?.takeIf { isTradingOver() }
		?.let { (startPrice + SupplyDemand.priceModifierAt(it)).coerceIn(Offer.MIN_PRICE, Offer.MAX_PRICE) }

	/** For s03 and the log once the price has changed (S05-2), even when nobody traded: "ISO price at Mu Herculis: 11 → 10 HT". */
	fun priceText(): String? = newPrice()?.let { "${good.goods.name} price at ${good.system.displayName}: $startPrice → $it HT" }

	/**
	 * Whom the wholesale step waits for (R37, S05-4, S05-6): once trading is over, the next of [wholesalers] who hasn't
	 * answered; else null.
	 */
	fun awaitingWholesaler(): String? = wholesalers.getOrNull(wholesales.size)?.takeIf { isTradingOver() }

	/**
	 * Whether nothing is left to decide: the Dictator has decided, no offer can trade any more, and everyone asked to
	 * trade wholesale has answered.
	 */
	fun isFinished(): Boolean = awaitingDictator() == null && awaitingTrade() == null && awaitingWholesaler() == null

	/** "Dictator" or "Wholesaler": why [email] is asked in the wholesale step, for the log and s03 (S05-5). */
	fun wholesaleRole(email: String): String = if (email == dictator) "Dictator" else "Wholesaler"

	/**
	 * The awaited wholesaler's trade as an offer of [type] at the new price (R37), so it settles like any trade: capped
	 * by money and by what their ships in port hold, split across them (R12, R10, R42).
	 */
	fun wholesaleOffer(type: OfferType): Offer {
		val email = awaitingWholesaler() ?: throw InvalidGameOperationException("No wholesale decision is pending.")
		return Offer(email, good.system, good.goods, type, checkNotNull(newPrice()))
	}

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
	 * This resolution after the awaited trade's player traded [units] (E05-S04): from 0, trading nothing, to the most the
	 * market allows, and made [positionRoll], if any (R43, E05-S06). Then the counter has moved, and the next offer it
	 * reaches is awaited.
	 */
	fun withTrade(units: Int, positionRoll: PositionRoll?): Resolution {
		val awaited = awaitingTrade() ?: throw InvalidGameOperationException("No trade is waiting for a decision.")
		check(units in 0..awaited.marketMax) { "$units u isn't within what the market allows, ${awaited.marketMax}" }
		return copy(trades = trades + awaited.traded(units, positionRoll))
	}

	/**
	 * This resolution after the awaited wholesaler traded [units] of [type] at the new price (R37), or passed with no
	 * [type] and 0 units (S05-4). Then the next one asked is awaited.
	 */
	fun withWholesale(type: OfferType?, units: Int): Resolution {
		val email = awaitingWholesaler() ?: throw InvalidGameOperationException("No wholesale decision is pending.")
		check(if (type == null) units == 0 else units in 1..Wholesale.MAX_UNITS) { "$units u isn't a wholesale of $type" }
		return copy(wholesales = wholesales + Wholesale(email, type, units, checkNotNull(newPrice())))
	}

	/**
	 * Who trades next and how much at most (R34), once the counter has started, or null when no offer that hasn't traded
	 * allows a unit (S03-3). The first trade is the winning offer, the highest buy if the counter is 0 or more, else the
	 * lowest sell (S03-2); when that side has no offer able to trade, it goes as every later one does: the most units at
	 * the current counter, a buyer–seller tie by the counter's sign (0 or more: the buyer), then [initiative] order.
	 */
	private fun nextTrade(): AwaitedTrade? {
		val counter = checkNotNull(counter())
		val able = offers.filter { offer -> trades.none { it.offer == offer } && maxUnitsOf(offer) >= 1 }
		val favoured = if (counter >= 0) OfferType.BUY else OfferType.SELL
		val byInitiative = compareBy<Offer> { offer -> initiative.indexOf(offer.playerEmail).takeIf { it >= 0 } ?: Int.MAX_VALUE }
		val winners = if (trades.isEmpty()) able.filter { it.type == favoured } else emptyList()
		val winner = winners.sortedWith(compareBy(Offer::bestPriceFirst).then(byInitiative)).firstOrNull()
		val mostUnits = able.sortedWith(compareByDescending(::maxUnitsOf).thenBy { it.type != favoured }.then(byInitiative)).firstOrNull()
		return (winner ?: mostUnits)?.let { AwaitedTrade(it, maxUnitsOf(it)) }
	}

	/** The most units [offer] allows at the counter now, from the start price (R34). */
	private fun maxUnitsOf(offer: Offer): Int = offer.maxUnits(checkNotNull(counter()), startPrice)

	private fun requireDictatorToDecide() {
		if (awaitingDictator() == null) {
			throw InvalidGameOperationException("No Dictator decision is pending.")
		}
	}

	companion object {
		/**
		 * [good] starting to resolve at [startPrice]: of [turnOffers], those for [good] are disclosed (S02-1), and
		 * [dictator], if any, is asked before the roll (S02-2, S02-4). [initiative] is the turn's, for ties (R34). Who the
		 * wholesale step will ask is fixed now, so a position roll during it changes nothing here (E05-S06-5): of [wholesalers],
		 * those without an offer for it, and the Dictator even if they place one (R37), in [initiative] order (S05-6).
		 */
		fun start(
			good: MarketGood,
			startPrice: Int,
			turnOffers: List<Offer>,
			dictator: String?,
			wholesalers: List<String>,
			initiative: List<String>
		): Resolution {
			val offers = turnOffers.filter { it.isFor(good) }
			val asked = (wholesalers.filter { email -> offers.none { it.playerEmail == email } } + listOfNotNull(dictator)).distinct()
				.sortedBy { email -> initiative.indexOf(email).takeIf { it >= 0 } ?: Int.MAX_VALUE }
			return Resolution(good, startPrice, offers, dictator, false, null, emptyList(), initiative, asked, emptyList())
		}
	}
}
