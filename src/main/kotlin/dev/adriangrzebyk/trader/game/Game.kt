package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem

/** [turn] is null until the game starts; [log] holds the public events, oldest first, each with its turn. */
data class Game(
	val id: String,
	val turns: Int,
	val status: GameStatus,
	val creatorEmail: String,
	val players: List<Player>,
	val turn: Turn?,
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
	 * charges the bids and rolls the initiative order with [dice] (R27). After the last turn's Trading (R41) the game
	 * is over and keeps its last turn. No clock calls this (D09): a phase ends only when its players are through, and
	 * the caller runs it inside [GameStore.update] so it happens once.
	 */
	fun advancePhase(dice: Dice): Game {
		val current = runningTurn()
		return when (current.phase) {
			TurnPhase.BIDDING -> closeBidding(current, dice)
			TurnPhase.SHIP_JUMPS -> copy(turn = current.toTrading())
			TurnPhase.TRADING -> if (current.number == turns) copy(status = GameStatus.FINISHED) else copy(turn = current.next())
		}
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
	 * [email] resolves the next good in the queue (R25, S01-4), which only the first player in initiative may. Each
	 * resolution is logged: "Resolving: Mu Herculis ISO (chosen by Vega Lines)" (S01-5).
	 */
	fun resolveNextGood(email: String): Game {
		val current = resolverTurnOf(email)
		val good = current.nextGood()
		return copy(
			turn = current.resolveNext(),
			log = log + LogEntry(current.number, "Resolving: ${good.label()} (chosen by ${corporationOf(email)})")
		)
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
			return Game(id, turns, GameStatus.LOBBY, creator.email, listOf(creator), null, emptyList())
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
