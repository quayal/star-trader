package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.OfferType

/**
 * The turn a running game is in and its phase (R41: a game starts in turn 1, Bidding).
 * [doneBidding] lists, in signal order, the players who have signalled done during this turn's Bidding;
 * it's public, so everyone can see who the game is waiting for (D09), and empty outside Bidding.
 * [offers] are this turn's trade offers in the order they were placed. Each is secret to its player (R17), so
 * nothing outside a player's own view may carry them; they stay through Trading and the next turn starts with none.
 * [bids] are this turn's initiative bids, at most one per player and each secret to its player (R29); like offers
 * they stay for the whole turn. [initiative] is every player's email in initiative order (R27), public, set when
 * Bidding closes and empty before. [turnOrderPicks] are the places players picked in this turn's turn order (R26), in
 * pick order; public, and empty until the first pick. [jumpedShips] names the ships that jumped this turn (each may
 * jump once, R26; names are unique in a game) and [doneJumping] the players whose jump slot has ended, in order; both
 * public and empty until the first jump. [landingShip] is the ship that jumped and whose player hasn't chosen where it
 * ends up yet (R30, S03); public, and null when no ship waits to land.
 */
data class Turn(
	val number: Int,
	val phase: TurnPhase,
	val doneBidding: List<String>,
	val offers: List<Offer>,
	val bids: List<InitiativeBid>,
	val initiative: List<String>,
	val turnOrderPicks: List<TurnOrderPick>,
	val jumpedShips: List<String>,
	val doneJumping: List<String>,
	val landingShip: String?,
	val tradingQueue: List<MarketGood>,
	val resolvedGoods: List<MarketGood>,
	val resolution: Resolution?
) {

	/** [email]'s own offers this turn: the only offers a player may ever be shown (R17, A05). */
	fun offersOf(email: String): List<Offer> = offers.filter { it.playerEmail == email }

	/** [email]'s own initiative bid this turn, or null if they haven't placed one: the only bid they may see (R29). */
	fun bidOf(email: String): InitiativeBid? = bids.firstOrNull { it.playerEmail == email }

	/**
	 * The turn order's places, first to last, each holding the email of the player who picked it or null while it's
	 * free. It has a place per player once Bidding has closed (one per initiative entry) and none before.
	 */
	fun turnOrder(): List<String?> = List(initiative.size) { index -> turnOrderPicks.firstOrNull { it.place == index + 1 }?.playerEmail }

	/** Whose pick it is (R26): the first player in initiative order without a place, or null when nobody is picking. */
	fun picker(): String? {
		if (phase != TurnPhase.SHIP_JUMPS) {
			return null
		}
		return initiative.firstOrNull { email -> turnOrderPicks.none { it.playerEmail == email } }
	}

	/**
	 * Whose jump slot it is (R26, S02): once the turn order is set, the first player in it whose slot hasn't ended, or
	 * null while players still pick, after the last slot, and outside Ship-jumps.
	 */
	fun jumper(): String? {
		if (phase != TurnPhase.SHIP_JUMPS || initiative.isEmpty() || picker() != null) {
			return null
		}
		return turnOrder().filterNotNull().firstOrNull { it !in doneJumping }
	}

	/** Refuses anything but Bidding (A04): a bid, offer or done signal after Bidding closed isn't applied. */
	fun requireBidding() {
		if (phase != TurnPhase.BIDDING) {
			throw PhaseClosedException(TurnPhase.BIDDING)
		}
	}

	/** Refuses anything but Ship-jumps: before it, it hasn't started; after it, it has closed (A04). */
	fun requireShipJumps() {
		when (phase) {
			TurnPhase.BIDDING -> throw InvalidGameOperationException("Ship-jumps hasn't started.")
			TurnPhase.TRADING -> throw PhaseClosedException(TurnPhase.SHIP_JUMPS)
			TurnPhase.SHIP_JUMPS -> Unit
		}
	}

	/** This turn after [email] signalled done bidding (R17: a readiness flag, so a repeat changes nothing). */
	fun signalDoneBidding(email: String): Turn {
		requireBidding()
		return if (email in doneBidding) this else copy(doneBidding = doneBidding + email)
	}

	/** Whether every one of [emails] has signalled done bidding (D09). */
	fun everyoneDoneBidding(emails: List<String>): Boolean = emails.all { it in doneBidding }

	/** This turn with [bid], replacing its player's earlier bid if any, so changing it never charges twice (S05-2). */
	fun placeBid(bid: InitiativeBid): Turn {
		requireBidding()
		return copy(bids = bids.filter { it.playerEmail != bid.playerEmail } + bid)
	}

	/** This turn with [offer]: at most one offer per player per good per system per turn (R33). */
	fun placeOffer(offer: Offer): Turn {
		requireBidding()
		if (offersOf(offer.playerEmail).any { it.system == offer.system && it.goods == offer.goods }) {
			throw InvalidOfferException("You already have an offer on ${offer.goods.name} at ${offer.system.displayName} this turn.")
		}
		return copy(offers = offers + offer)
	}

	/** What [email] pays when Bidding closes (R27): their bid, or 0 if they never placed one (Q07, proposed). */
	fun bidAmountOf(email: String): Int = bidOf(email)?.amount ?: 0

	/**
	 * Bidding → Ship-jumps (R27): [emails] are put in initiative order by bid + d2 from [dice], ties rolled again. The
	 * bids stay on the turn, secret.
	 */
	fun closeBidding(emails: List<String>, dice: Dice): Turn {
		val order = initiativeOrder(emails, emails.associateWith { bidAmountOf(it) }, dice)
		return copy(phase = TurnPhase.SHIP_JUMPS, doneBidding = emptyList(), initiative = order)
	}

	/**
	 * This turn after [email] picked [place] (1 = first) in the turn order (R26). A place that isn't free is refused.
	 * When only one place is left, its player gets it without being asked (S01-6, proposed). Checking that it's
	 * [email]'s pick is the caller's, which knows the players' names.
	 */
	fun pickPlace(email: String, place: Int?): Turn {
		requireShipJumps()
		val order = turnOrder()
		if (place == null || place !in 1..order.size || order[place - 1] != null) {
			throw InvalidPlaceException()
		}
		return withPick(TurnOrderPick(email, place)).withLastPlaceFilled()
	}

	private fun withPick(pick: TurnOrderPick): Turn = copy(turnOrderPicks = turnOrderPicks + pick)

	/** S01-6, proposed: when only one place is left, its player gets it without being asked. */
	private fun withLastPlaceFilled(): Turn {
		val lastPicker = picker()
		if (lastPicker == null || turnOrder().count { it == null } != 1) {
			return this
		}
		return withPick(TurnOrderPick(lastPicker, turnOrder().indexOf(null) + 1))
	}

	/**
	 * Whose jump slot it is, once checked that this is Ship-jumps and the turn order is set (R26). Checking that it's the
	 * caller's slot is the caller's, which knows the players' names.
	 */
	fun currentJumper(): String {
		requireShipJumps()
		if (picker() != null) {
			throw InvalidGameOperationException("The turn order isn't set yet.")
		}
		// Only a game saved before initiative existed (E03-S05) can be in Ship-jumps with nobody to jump.
		return jumper() ?: throw InvalidGameOperationException("Nobody is jumping now.")
	}

	/** S02-3, S03-6: a jump isn't finished until its ship has landed, so nothing else happens in the slot until then. */
	fun refuseWhileAShipWaitsToLand() {
		landingShip?.let { throw InvalidGameOperationException("Choose where $it ends up first.") }
	}

	/** Whether [ship] has already jumped this turn (each ship jumps at most once, R26). */
	fun hasJumped(ship: Ship): Boolean = ship.name in jumpedShips

	/** Whether every one of [ships] has jumped this turn. */
	fun haveAllJumped(ships: List<Ship>): Boolean = ships.all { hasJumped(it) }

	/** This turn after [ship] jumped: it can't jump again, and it waits to land (S03). */
	fun recordJump(ship: Ship): Turn = copy(jumpedShips = jumpedShips + ship.name, landingShip = ship.name)

	/** The name of the ship waiting to land (S03), refused when there is none. */
	fun shipWaitingToLand(): String = landingShip ?: throw InvalidGameOperationException("No ship is waiting to land.")

	/** This turn after the ship waiting to land has landed. */
	fun landed(): Turn = copy(landingShip = null)

	/** This turn after [email]'s jump slot ended; after the last one the phase moves to Trading (S02-8). */
	fun endJumpSlot(email: String): Turn {
		val ended = copy(doneJumping = doneJumping + email)
		return if (ended.jumper() == null) ended.toTrading() else ended
	}

	/** Ship-jumps → Trading (N06): every good at every system is queued to resolve (R36, S01-2). */
	fun toTrading(): Turn =
		copy(phase = TurnPhase.TRADING, tradingQueue = MarketGood.everyOnBoard(), resolvedGoods = emptyList(), resolution = null)

	/** Refuses anything but Trading: before it, it hasn't started (A04). */
	fun requireTrading() {
		if (phase != TurnPhase.TRADING) {
			throw InvalidGameOperationException("Trading hasn't started.")
		}
	}

	/**
	 * Who orders the queue and resolves each good (R25): the first player in initiative order. Checking that it's the
	 * caller is the caller's, which knows the players' names.
	 */
	fun firstInInitiative(): String =
		initiative.firstOrNull() ?: throw InvalidGameOperationException("Nobody is first in initiative this turn.")

	/**
	 * This turn with the goods still to resolve in [order] (R25, S01-3). [order] must hold exactly the goods still queued:
	 * one resolved in between makes it stale, and it's refused rather than half-applied (A04).
	 */
	fun reorderQueue(order: List<MarketGood>): Turn {
		requireTrading()
		if (order.size != tradingQueue.size || order.toSet() != tradingQueue.toSet()) {
			throw InvalidGameOperationException("The remaining goods have changed. Try again.")
		}
		return copy(tradingQueue = order)
	}

	/** The good that resolves next (R25), refused once every good has resolved. */
	fun nextGood(): MarketGood {
		requireTrading()
		return tradingQueue.firstOrNull() ?: throw InvalidGameOperationException("Every good has been resolved.")
	}

	/**
	 * This turn after the next good started resolving (R25, S01-4) at [startPrice], the game's price for it (R34): its offers are
	 * disclosed (E05-S02-1) and [dictator], that good's Dictator at that system if it has one, is asked before the roll
	 * (S02-2). With no Dictator the counter starts at once with a roll from [dice] (S02-4, E05-S03-1). [wholesalers] are
	 * the good's Wholesalers there, asked after the price changes if they have no offer for it (R37). Refused while the
	 * last good is still resolving.
	 */
	fun resolveNext(dictator: String?, wholesalers: List<String>, startPrice: Int, dice: Dice): Turn {
		requireTrading()
		if (resolution?.isFinished() == false) {
			throw InvalidGameOperationException("${resolution.good.label()} hasn't finished resolving.")
		}
		val good = nextGood()
		return copy(tradingQueue = tradingQueue.drop(1)).withResolution(
			Resolution.start(good, startPrice, offers, dictator, wholesalers, initiative),
			dice
		)
	}

	/**
	 * Whom the resolution waits for to place an offer or pass (R38), refused when nobody is asked. Checking that it's the
	 * caller is the caller's, which knows the players' names.
	 */
	fun dictatorToDecide(): String {
		requireTrading()
		return resolution?.awaitingDictator() ?: throw InvalidGameOperationException("No Dictator decision is pending.")
	}

	/**
	 * This turn after the Dictator placed [offer] for the good resolving (R38, S02-2, S02-3); then the counter starts
	 * with a roll from [dice] (S03-1).
	 */
	fun placeDictatorOffer(offer: Offer, dice: Dice): Turn = withResolution(resolutionNow().withDictatorOffer(offer), dice)

	/** This turn after the Dictator passed (S02-2); then the counter starts with a roll from [dice] (S03-1). */
	fun dictatorPassed(dice: Dice): Turn = withResolution(resolutionNow().withDictatorPassed(), dice)

	/**
	 * The trade the resolution waits for its player to choose an amount for (E05-S04-1), refused when there is none.
	 * Checking that it's the caller's is the caller's, which knows the players' names.
	 */
	fun tradeAwaited(): AwaitedTrade {
		requireTrading()
		return resolution?.awaitingTrade() ?: throw InvalidGameOperationException("No trade is waiting for a decision.")
	}

	/** This turn after the awaited trade's player traded [units] (E05-S04); once nothing is left, its good counts as resolved. */
	fun recordTrade(units: Int): Turn = withResolution(resolutionNow().withTrade(units))

	/**
	 * Whom the wholesale step waits for to trade or pass (R37, S05-4), refused when nobody is asked. Checking that it's the
	 * caller is the caller's, which knows the players' names.
	 */
	fun wholesalerToDecide(): String {
		requireTrading()
		return resolution?.awaitingWholesaler() ?: throw InvalidGameOperationException("No wholesale decision is pending.")
	}

	/** This turn after the wholesaler asked traded [units] of [type], or passed (S05-4); after the last, its good counts as resolved. */
	fun recordWholesale(type: OfferType?, units: Int): Turn = withResolution(resolutionNow().withWholesale(type, units))

	private fun resolutionNow(): Resolution {
		requireTrading()
		return resolution ?: throw InvalidGameOperationException("No Dictator decision is pending.")
	}

	/** This turn with [changed] as its resolution, its counter started with [dice] once nobody is asked any more (R34). */
	private fun withResolution(changed: Resolution, dice: Dice): Turn = withResolution(changed.startCounterOnceDecided(dice))

	/** This turn with [current] as its resolution; once it has finished, its good counts as resolved. */
	private fun withResolution(current: Resolution): Turn =
		copy(resolution = current, resolvedGoods = if (current.isFinished()) resolvedGoods + current.good else resolvedGoods)

	/** The start of the turn after this one. */
	fun next(): Turn = bidding(number + 1)

	/**
	 * [emails] ordered by [base] + d2, highest first (R27). Players who tie roll d2 again among themselves, and only
	 * those rolls order them, until no tie is left. Rolls are taken in [emails]' order.
	 */
	private fun initiativeOrder(emails: List<String>, base: Map<String, Int>, dice: Dice): List<String> {
		val scores = emails.associateWith { base.getValue(it) + dice.d2() }
		return emails.groupBy { scores.getValue(it) }.toSortedMap(reverseOrder()).values.flatMap { tied ->
			if (tied.size == 1) tied else initiativeOrder(tied, tied.associateWith { 0 }, dice)
		}
	}

	companion object {
		/**
		 * The start of turn [number]: Bidding, with nobody done, no offers or bids, no initiative, turn order, jumps or
		 * Trading queue yet.
		 */
		fun bidding(number: Int) = Turn(
			number,
			TurnPhase.BIDDING,
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			null,
			emptyList(),
			emptyList(),
			null
		)
	}
}
