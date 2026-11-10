package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * [turn] is null until the game starts; [prices] are each good's price at each system in this game, which Trading moves
 * (R35); [log] holds the public events, oldest first, each with its turn.
 */
data class Game(
	val id: String,
	val turns: Int,
	val status: GameStatus,
	val creatorEmail: String,
	val players: List<Player>,
	val turn: Turn?,
	val prices: MarketPrices,
	val log: List<LogEntry>
) {

	fun addPlayer(player: Player): Game {
		if (status != GameStatus.LOBBY) {
			throw InvalidGameOperationException("This game has already started")
		}
		if (players.size >= MAX_PLAYERS) {
			throw InvalidGameOperationException("This game is full")
		}
		if (hasPlayer(player.email)) {
			throw InvalidGameOperationException("This player is already in the game")
		}
		return copy(players = players + player)
	}

	fun hasPlayer(email: String): Boolean = players.any { it.email == email }

	/**
	 * Closes joining and sets every player up (R40, R41): 300 HT and two empty ships at the Ports of two
	 * different systems from [picker], then turn 1 Bidding. Only the creator may start, once, with 2+ players.
	 */
	fun start(callerEmail: String, picker: StartingSystemPicker): Game {
		if (callerEmail != creatorEmail) {
			throw NotTheCreatorException()
		}
		if (status != GameStatus.LOBBY) {
			throw InvalidGameOperationException("This game has already started")
		}
		if (players.size < MIN_PLAYERS_TO_START) {
			throw InvalidGameOperationException("At least $MIN_PLAYERS_TO_START players are needed to start")
		}

		val setUp = players.mapIndexed { index, player ->
			val (first, second) = picker.pickTwoDifferentSystems()
			val ships = listOf(
				Ship.emptyAt(Ship.NAMES[index * 2], first, ShipLocation.PORT),
				Ship.emptyAt(Ship.NAMES[index * 2 + 1], second, ShipLocation.PORT)
			)
			player.startWith(STARTING_MONEY, ships)
		}
		return copy(
			status = GameStatus.RUNNING,
			players = setUp,
			turn = Turn.bidding(1),
			log = log + LogEntry(1, "Game started: ${setUp.size} players, $turns turns") +
				LogEntry(1, "Starting positions: " + setUp.joinToString("; ") { it.startingPositions() })
		)
	}

	/**
	 * Moves to the next phase (N06, R04): Bidding → Ship-jumps → Trading → the next turn's Bidding. Closing Bidding
	 * charges the bids and rolls the initiative order with [dice] (R27); leaving Trading ends the turn ([endTurn]). No
	 * clock calls this (D09): a phase ends only when its players are through, and the caller runs it inside
	 * [GameStore.update] so it happens once.
	 */
	fun advancePhase(dice: Dice): Game {
		val current = runningTurn()
		return when (current.phase) {
			TurnPhase.BIDDING -> closeBidding(current, dice)
			TurnPhase.SHIP_JUMPS -> copy(turn = current.toTrading())
			TurnPhase.TRADING -> endTurn(current)
		}
	}

	/**
	 * Ends [current], whose Trading is over (E05-S07, N06): logged "All goods resolved" in that turn. After the last
	 * turn the creator chose (R39, R41) the game is over and keeps its last turn; otherwise the next turn starts with
	 * Bidding, with no offers, so offers that didn't trade expire (S07-2, proposed), and its first log line starts a new
	 * group in the log (S07-3). This is the one place a turn ends and the next starts: anything that takes effect "from
	 * the next turn" (E05-S06's position changes, R43) applies here, before the next turn's Bidding.
	 */
	private fun endTurn(current: Turn): Game {
		val ended = log + LogEntry(current.number, ALL_GOODS_RESOLVED)
		if (current.number >= turns) {
			return copy(status = GameStatus.FINISHED, turn = current, log = ended)
		}
		val next = current.next()
		return copy(
			turn = next,
			log = ended + LogEntry(next.number, "Turn ${next.number} of $turns starts with Bidding; turn ${current.number}'s offers have expired")
		)
	}

	/**
	 * Records that [email] is done bidding (R17). Bidding closes the moment the last player signals done (D09), rolling
	 * the initiative order with [dice]; a signal after that is refused (A04).
	 */
	fun signalDoneBidding(email: String, dice: Dice): Game {
		playerOf(email)
		val signalled = copy(turn = runningTurn().signalDoneBidding(email))
		return if (signalled.runningTurn().everyoneDoneBidding(players.map { it.email })) signalled.advancePhase(dice) else signalled
	}

	/**
	 * Places or changes [email]'s secret initiative bid (R27, S05-1, S05-2): the player checks the amount ([Player.bid]),
	 * the turn records it ([Turn.placeBid]). A bid after Bidding closed is refused as such, whatever its amount (A04).
	 */
	fun placeInitiativeBid(email: String, amount: Int?): Game {
		val player = playerOf(email)
		val current = runningTurn()
		current.requireBidding()
		return copy(turn = current.placeBid(player.bid(amount)))
	}

	/**
	 * Places [email]'s secret offer (R17) for this turn: the player makes it ([Player.offer]), the turn records it
	 * ([Turn.placeOffer]). An offer after Bidding closed is refused as such, whatever it says (A04).
	 */
	fun placeOffer(email: String, system: StarSystem, goods: Goods, type: OfferType, price: Int?): Game {
		val player = playerOf(email)
		val current = runningTurn()
		current.requireBidding()
		return copy(turn = current.placeOffer(player.offer(system, goods, type, price)))
	}

	/**
	 * [email] picks [place] (1 = first) in this turn's turn order (R26). Players pick one at a time in initiative order
	 * (R27); out of turn is refused, and [Turn.pickPlace] checks the place and fills the last one. Each pick is logged,
	 * then the full order (S01-5).
	 */
	fun pickTurnOrderPlace(email: String, place: Int?): Game {
		playerOf(email)
		val current = runningTurn()
		current.requireShipJumps()
		val picker = current.picker() ?: throw InvalidGameOperationException("The turn order is already set.")
		if (picker != email) {
			throw NotYourTurnException("It's ${corporationOf(picker)}'s turn to choose a place.")
		}
		val settled = current.pickPlace(email, place)
		val picks = settled.turnOrderPicks.drop(current.turnOrderPicks.size)
		// The first pick is [email]'s own; a second is the last place, filled without asking.
		val events = picks.mapIndexed { index, pick ->
			val corporation = corporationOf(pick.playerEmail)
			if (index == 0) "$corporation chose ${ordinal(pick.place)}" else "$corporation gets ${ordinal(pick.place)}, the only place left"
		}.toMutableList()
		if (settled.picker() == null) {
			events += "Turn order (for this turn): " + settled.turnOrder().joinToString(" → ") { corporationOf(checkNotNull(it)) }
		}
		return copy(turn = settled, log = log + events.map { LogEntry(current.number, it) })
	}

	/**
	 * [email] jumps their ship [shipName] to [destination] during their slot (R26, S02); see [Ship.jumpTo]. Each ship
	 * jumps at most once per turn, and nothing else happens while a ship waits to land (S03). The jump is logged (S02-7).
	 */
	fun jumpShip(email: String, shipName: String?, destination: StarSystem?): Game {
		val current = jumpSlotOf(email)
		current.refuseWhileAShipWaitsToLand()
		val player = playerOf(email)
		val ship = player.ship(shipName)?.takeUnless { current.hasJumped(it) }
			?: throw InvalidJumpException("Choose one of your ships that hasn't jumped this turn.")
		if (destination == null) {
			throw InvalidJumpException("Choose a destination system.")
		}
		val moved = player.jumpShip(ship, destination)
		return copy(
			players = replace(moved),
			turn = current.recordJump(ship),
			log = log + LogEntry(current.number, "Jump: ${player.corporationName} — ${checkNotNull(moved.ship(shipName)).jumpLog()}")
		)
	}

	/**
	 * [email] chooses where the ship that just jumped ends up (R30, S03). That finishes the jump; the landing is logged
	 * (S03-3), and landing the player's last ship ends their slot (S02-6, S03-4).
	 */
	fun landShip(email: String, location: ShipLocation?): Game {
		val current = jumpSlotOf(email)
		val shipName = current.shipWaitingToLand()
		if (location == null) {
			throw InvalidLandingException()
		}
		val landed = playerOf(email).landShip(shipName, location)
		val game = copy(
			players = replace(landed),
			turn = current.landed(),
			log = log + LogEntry(current.number, "Landing: ${landed.corporationName} — ${checkNotNull(landed.ship(shipName)).landingLog()}")
		)
		return if (current.haveAllJumped(landed.ships)) game.endJumpSlot(email) else game
	}

	/** [email] ends their jump slot (S02-6): ships they didn't jump stay where they are, and the next player's slot starts. */
	fun finishJumping(email: String): Game {
		jumpSlotOf(email).refuseWhileAShipWaitsToLand()
		return endJumpSlot(email)
	}

	/**
	 * [email] puts the goods still to resolve in [order] (R25, S01-3); only the first player in initiative may, at any
	 * time during Trading, and [Turn.reorderQueue] refuses an order that's gone stale. Not logged: only the resolutions are.
	 */
	fun reorderTradingQueue(email: String, order: List<MarketGood>): Game = copy(turn = resolverTurnOf(email).reorderQueue(order))

	/**
	 * [email] resolves the next good in the queue (R25, S01-4), which only the first player in initiative may, and only
	 * once the last good has finished resolving. Its offers are disclosed (E05-S02-1), and its Dictator at that system,
	 * if any, is asked before the roll (S02-2); with none, the counter starts with a roll from [dice] (E05-S03). All of
	 * it is logged: "Resolving: Mu Herculis ISO (chosen by Vega Lines)" (S01-5), the offers, then the counter.
	 */
	fun resolveNextGood(email: String, dice: Dice): Game {
		val current = resolverTurnOf(email)
		current.resolution?.awaitingDictator()?.let { throw InvalidGameOperationException(waitingForDictator(it)) }
		current.resolution?.awaitingTrade()?.let { throw InvalidGameOperationException(waitingForTrader(it)) }
		current.resolution?.let { previous ->
			previous.awaitingWholesaler()?.let { throw InvalidGameOperationException(waitingForWholesaler(previous)) }
		}
		val good = current.nextGood()
		val dictator = players.firstOrNull { it.isDictatorOf(good.system, good.goods) }
		val wholesalers = players.filter { it.isWholesalerOf(good) }.map { it.email }
		val started = current.resolveNext(dictator?.email, wholesalers, prices.priceOf(good), dice)
		val resolution = checkNotNull(started.resolution)
		return withResolutionMoved(
			started,
			null,
			listOf("Resolving: ${good.label()} (chosen by ${corporationOf(email)})", disclosure(resolution))
		)
	}

	/**
	 * [email], the Dictator asked, places their one offer for the good resolving (R38, S02-2): checked like any offer
	 * (R03), then disclosed like the others (S02-3) and logged: "Altair Trade Co. (Dictator) offers to sell at 10 HT".
	 * Then the counter starts with a roll from [dice] (E05-S03).
	 */
	fun placeDictatorOffer(email: String, type: OfferType, price: Int?, dice: Dice): Game {
		val current = dictatorTurnOf(email)
		val offer = playerOf(email).dictatorOffer(checkNotNull(current.resolution).good, type, price)
		val decided = current.placeDictatorOffer(offer, dice)
		return withResolutionMoved(decided, current.resolution, listOf("${corporationOf(email)} (Dictator) offers to ${offer.terms()}"))
	}

	/**
	 * [email], the Dictator asked, passes (S02-2); logged: "Altair Trade Co. (Dictator) passed". Then the counter starts
	 * with a roll from [dice] (E05-S03).
	 */
	fun passAsDictator(email: String, dice: Dice): Game {
		val current = dictatorTurnOf(email)
		val decided = current.dictatorPassed(dice)
		return withResolutionMoved(decided, current.resolution, listOf("${corporationOf(email)} (Dictator) passed"))
	}

	/**
	 * [email], whose offer the counter has reached, trades [amount] units, split across their ships in port there as
	 * [loads] (E05-S04): [Player.trade] checks the amount against their max (R34, R12, R10) and the split (R42), and
	 * moves their money and cargo. The trade is public and logged (S04-6): "Vega Lines could buy up to 12 u: bought 10 u
	 * at 13 HT each, paid 130 HT". Then the counter reaches the next offer.
	 */
	fun trade(email: String, amount: Int?, loads: List<ShipLoad>): Game {
		val current = traderTurnOf(email)
		val awaited = current.tradeAwaited()
		val trader = playerOf(email).trade(awaited.offer, awaited.marketMax, amount, loads)
		return copy(players = replace(trader)).withTradeRecorded(current, checkNotNull(amount))
	}

	/**
	 * [email], whose offer the counter has reached, acknowledges that they can trade nothing (E05-S04-5): no money for a
	 * unit (R12), or none of the good in their ships in port there (R10). Refused while they can trade a unit: then they
	 * trade 1 to their max (R34). Logged like any trade, without the reason (R29): "Orion Freight could buy up to 3 u:
	 * bought nothing".
	 */
	fun tradeNothing(email: String): Game {
		val current = traderTurnOf(email)
		val awaited = current.tradeAwaited()
		val max = playerOf(email).tradeMax(awaited.offer, awaited.marketMax)
		if (max > 0) {
			throw InvalidGameOperationException("You can ${awaited.offer.type.name.lowercase()} up to $max u: choose from 1 to $max.")
		}
		return withTradeRecorded(current, 0)
	}

	private fun withTradeRecorded(current: Turn, units: Int): Game =
		withResolutionMoved(current.recordTrade(units), current.resolution, emptyList())

	/**
	 * [email], asked in the wholesale step (R37, E05-S05-4), buys or sells ([type]) [amount] units at the good's new price,
	 * split across their ships in port there as [loads]: settled like any trade ([Player.trade]), with at most 5 units,
	 * capped by money and cargo (R12, R10, R42). It doesn't move the price. Logged (S05-5): "Kessler Cargo (Wholesaler)
	 * bought 3 u at 10 HT each, paid 30 HT".
	 */
	fun wholesale(email: String, type: OfferType?, amount: Int?, loads: List<ShipLoad>): Game {
		val current = wholesalerTurnOf(email)
		if (type == null) {
			throw InvalidTradeException("Choose Buy or Sell, or pass.")
		}
		val offer = checkNotNull(current.resolution).wholesaleOffer(type)
		val trader = playerOf(email).trade(offer, Wholesale.MAX_UNITS, amount, loads)
		return copy(
			players = replace(trader)
		).withResolutionMoved(current.recordWholesale(type, checkNotNull(amount)), current.resolution, emptyList())
	}

	/** [email], asked in the wholesale step, passes (S05-4: always possible); logged: "Kessler Cargo (Wholesaler) passed on wholesale". */
	fun passWholesale(email: String): Game {
		val current = wholesalerTurnOf(email)
		return withResolutionMoved(current.recordWholesale(null, 0), current.resolution, emptyList())
	}

	/**
	 * This game with [after] as its turn, once its resolution moved on from [before] (null for a good that just started
	 * resolving). [events] are logged first, then what the resolution did. The moment no offer can trade any more, the
	 * good's new price replaces its old one in [prices] (R35, S05-3). Once the last good has finished resolving, the turn
	 * ends in the same step (E05-S07, D09: nobody has to press anything, and it happens once, A04).
	 */
	private fun withResolutionMoved(after: Turn, before: Resolution?, events: List<String>): Game {
		val resolution = checkNotNull(after.resolution)
		val repriced = resolution.newPrice()?.takeIf { before?.isTradingOver() != true }
		val moved = copy(
			turn = after,
			prices = repriced?.let { prices.withPrice(resolution.good, it) } ?: prices,
			log = log + events.map { LogEntry(after.number, it) } + resolutionLog(after.number, before, resolution)
		)
		return if (after.isEveryGoodResolved()) moved.endTurn(after) else moved
	}

	/**
	 * The public log lines of what [resolution] did in turn [turn] since [before] (null for a good that just started
	 * resolving), as it happens (S03-6, D07: no dice arithmetic): "Mu Herculis ISO: market +3 → buyers first" when the
	 * counter starts, each trade when it's made ("Vega Lines could buy up to 12 u: bought 10 u at 13 HT each, paid 130
	 * HT"), then once trading is over "No one else can trade at Mu Herculis ISO" and the price change, "ISO price at Mu
	 * Herculis: 11 → 10 HT" (S05-2), then each wholesale answer (S05-5). None while the Dictator is still asked.
	 */
	private fun resolutionLog(turn: Int, before: Resolution?, resolution: Resolution): List<LogEntry> {
		val market = resolution.marketText() ?: return emptyList()
		val label = resolution.good.label()
		val started = listOfNotNull("$label: $market".takeIf { before?.startingCounter == null })
		val trades = resolution.trades.drop(before?.trades?.size ?: 0).map { "${corporationOf(it.offer.playerEmail)} ${it.terms()}" }
		val ended = if (resolution.isTradingOver() && before?.isTradingOver() != true) {
			listOf("${resolution.endText()} at $label", checkNotNull(resolution.priceText()))
		} else {
			emptyList()
		}
		val wholesales = resolution.wholesales.drop(before?.wholesales?.size ?: 0)
			.map { "${corporationOf(it.playerEmail)} (${resolution.wholesaleRole(it.playerEmail)}) ${it.terms()}" }
		return (started + trades + ended + wholesales).map { LogEntry(turn, it) }
	}

	/** The running turn, once checked that its resolution waits for [email] to trade wholesale or pass (R37, R04). */
	private fun wholesalerTurnOf(email: String): Turn {
		playerOf(email)
		val current = runningTurn()
		if (current.wholesalerToDecide() != email) {
			throw NotYourTurnException(waitingForWholesaler(checkNotNull(current.resolution)))
		}
		return current
	}

	/** S01-8 (D09): "Waiting for Kessler Cargo (Wholesaler) to trade up to 5 u at 10 HT, or pass." */
	private fun waitingForWholesaler(resolution: Resolution): String {
		val email = checkNotNull(resolution.awaitingWholesaler())
		val price = checkNotNull(resolution.newPrice())
		return "Waiting for ${corporationOf(
			email
		)} (${resolution.wholesaleRole(email)}) to trade up to ${Wholesale.MAX_UNITS} u at $price HT, or pass."
	}

	/** The running turn, once checked that its resolution waits for [email], as Dictator, to decide (R38, R04). */
	private fun dictatorTurnOf(email: String): Turn {
		playerOf(email)
		val current = runningTurn()
		val dictator = current.dictatorToDecide()
		if (dictator != email) {
			throw NotYourTurnException(waitingForDictator(dictator))
		}
		return current
	}

	/** The running turn, once checked that its resolution waits for [email] to choose an amount (E05-S04, R04). */
	private fun traderTurnOf(email: String): Turn {
		playerOf(email)
		val current = runningTurn()
		val awaited = current.tradeAwaited()
		if (awaited.offer.playerEmail != email) {
			throw NotYourTurnException(waitingForTrader(awaited))
		}
		return current
	}

	/** S01-8 (D09): "Vega Lines can buy up to 4 u — waiting for their decision." The counter's max: money stays private (R29). */
	private fun waitingForTrader(awaited: AwaitedTrade): String {
		val verb = awaited.offer.type.name.lowercase()
		return "${corporationOf(awaited.offer.playerEmail)} can $verb up to ${awaited.marketMax} u — waiting for their decision."
	}

	/** S01-8 (D09): "Waiting for Altair Trade Co. (Dictator) to decide." */
	private fun waitingForDictator(email: String): String = "Waiting for ${corporationOf(email)} (Dictator) to decide."

	/** The public log line disclosing [resolution]'s offers (S02-1): "Offers for Mu Herculis ISO: Vega Lines — buy at 11 HT". */
	private fun disclosure(resolution: Resolution): String {
		val label = resolution.good.label()
		if (resolution.offers.isEmpty()) {
			return "No offers for $label"
		}
		return "Offers for $label: " + resolution.offers.joinToString("; ") { "${corporationOf(it.playerEmail)} — ${it.terms()}" }
	}

	/** The running turn, once checked that it's Trading and [email] is first in initiative (R25, R04). */
	private fun resolverTurnOf(email: String): Turn {
		playerOf(email)
		val current = runningTurn()
		current.requireTrading()
		val resolver = current.firstInInitiative()
		if (resolver != email) {
			throw NotYourTurnException("Order decided by ${corporationOf(resolver)} (first in initiative).")
		}
		return current
	}

	/** The running turn, once checked that it's [email]'s jump slot (R04: an ordered step out of turn is refused). */
	private fun jumpSlotOf(email: String): Turn {
		playerOf(email)
		val current = runningTurn()
		val jumper = current.currentJumper()
		if (jumper != email) {
			throw NotYourTurnException("It's ${corporationOf(jumper)}'s turn to jump.")
		}
		return current
	}

	private fun endJumpSlot(email: String): Game = copy(turn = runningTurn().endJumpSlot(email))

	/**
	 * Bidding → Ship-jumps (R27): every player pays their bid, win or lose, and a player who never bid counts as
	 * bidding 0 and still rolls (Q07, proposed). The order is public and logged without amounts (R29); the bids stay
	 * secret on the turn.
	 */
	private fun closeBidding(current: Turn, dice: Dice): Game {
		val closed = current.closeBidding(players.map { it.email }, dice)
		return copy(
			players = players.map { it.pay(current.bidAmountOf(it.email)) },
			turn = closed,
			log = log + LogEntry(current.number, "Initiative order (bid + d2): " + closed.initiative.joinToString(" → ") { corporationOf(it) })
		)
	}

	/** The player whose membership key is [email]; refused when they aren't in this game. */
	private fun playerOf(email: String): Player = players.firstOrNull { it.email == email } ?: throw NotAGameMemberException()

	private fun replace(player: Player): List<Player> = players.map { if (it.email == player.email) player else it }

	private fun corporationOf(email: String): String = players.first { it.email == email }.corporationName

	private fun runningTurn(): Turn = when (status) {
		GameStatus.LOBBY -> throw InvalidGameOperationException("This game hasn't started")
		GameStatus.FINISHED -> throw InvalidGameOperationException("This game is over")
		GameStatus.RUNNING -> checkNotNull(turn) { "A running game always has a turn" }
	}

	companion object {
		const val MAX_PLAYERS = 6
		const val MIN_PLAYERS_TO_START = 2

		/** S07-1: the log line, and s03's words, once a turn's last good has resolved. */
		const val ALL_GOODS_RESOLVED = "All goods resolved"

		/** R40: every player starts with 300 HT. */
		const val STARTING_MONEY = 300

		/** A game lasts a whole number of turns from 1 to 50. */
		const val MIN_TURNS = 1
		const val MAX_TURNS = 50

		/** A new game of [turns] turns waiting in the lobby, with its creator as the only player. */
		fun lobby(id: String, turns: Int?, creator: Player): Game {
			if (turns == null || turns !in MIN_TURNS..MAX_TURNS) {
				throw InvalidGameOperationException("Enter a whole number of turns from $MIN_TURNS to $MAX_TURNS.")
			}
			return Game(id, turns, GameStatus.LOBBY, creator.email, listOf(creator), null, MarketPrices.board(), emptyList())
		}
	}
}

/** 1st, 2nd, 3rd, 4th … for a place in the turn order (players are at most six, so no 11th–13th). */
private fun ordinal(place: Int): String = place.toString() + when (place) {
	1 -> "st"
	2 -> "nd"
	3 -> "rd"
	else -> "th"
}
