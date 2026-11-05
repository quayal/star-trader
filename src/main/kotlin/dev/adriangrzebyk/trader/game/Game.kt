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
			player.copy(money = STARTING_MONEY, ships = ships)
		}
		val positions = setUp.joinToString("; ") { player ->
			"${player.corporationName} — " + player.ships.joinToString(", ") { "${it.name} at ${it.system.displayName}" }
		}
		return copy(
			status = GameStatus.RUNNING,
			players = setUp,
			turn = Turn.bidding(1),
			log = log + LogEntry(1, "Game started: ${setUp.size} players, $turns turns") + LogEntry(1, "Starting positions: $positions")
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
			TurnPhase.SHIP_JUMPS -> copy(turn = current.copy(phase = TurnPhase.TRADING))
			TurnPhase.TRADING -> if (current.number == turns) copy(status = GameStatus.FINISHED) else copy(turn = Turn.bidding(current.number + 1))
		}
	}

	/**
	 * Records that [email] is done bidding (R17: a readiness flag, not a commit, so a repeat changes nothing).
	 * Bidding closes the moment the last player signals done (D09), rolling the initiative order with [dice]; a
	 * signal after that is refused (A04).
	 */
	fun signalDoneBidding(email: String, dice: Dice): Game {
		if (!hasPlayer(email)) {
			throw NotAGameMemberException()
		}
		val current = runningTurn()
		if (current.phase != TurnPhase.BIDDING) {
			throw PhaseClosedException(TurnPhase.BIDDING)
		}
		if (email in current.doneBidding) {
			return this
		}
		val doneBidding = current.doneBidding + email
		val signalled = copy(turn = current.copy(doneBidding = doneBidding))
		return if (players.all { it.email in doneBidding }) signalled.advancePhase(dice) else signalled
	}

	/**
	 * Places or changes [email]'s secret initiative bid (R27, S05-1, S05-2): a whole number of HT from 0 up to their
	 * money, only while Bidding is open (A04). It's paid when Bidding closes, so changing it never charges twice.
	 */
	fun placeInitiativeBid(email: String, amount: Int?): Game {
		val player = players.firstOrNull { it.email == email } ?: throw NotAGameMemberException()
		val current = runningTurn()
		if (current.phase != TurnPhase.BIDDING) {
			throw PhaseClosedException(TurnPhase.BIDDING)
		}
		if (amount == null) {
			throw InvalidBidException("Enter a whole number of HT, 0 or more.")
		}
		if (amount < 0) {
			throw InvalidBidException("A bid can't be negative. Enter 0 to bid nothing.")
		}
		if (amount > player.money) {
			throw InvalidBidException("You have ${player.money} HT. Enter ${player.money} or less.")
		}
		val bids = current.bids.filter { it.playerEmail != email } + InitiativeBid(email, amount)
		return copy(turn = current.copy(bids = bids))
	}

	/**
	 * Places [email]'s secret offer (R17) for this turn. Only while Bidding is open (A04), for a good traded at
	 * [system], at a whole price from 1 to 20 (R03), at most one per good per system per turn (R33), and never for
	 * a good the player is Dictator of there, who places that offer when it resolves in Trading (R38).
	 */
	fun placeOffer(email: String, system: StarSystem, goods: Goods, type: OfferType, price: Int?): Game {
		val player = players.firstOrNull { it.email == email } ?: throw NotAGameMemberException()
		val current = runningTurn()
		if (current.phase != TurnPhase.BIDDING) {
			throw PhaseClosedException(TurnPhase.BIDDING)
		}
		if (goods !in system.goods) {
			throw InvalidOfferException("${goods.name} isn't traded at ${system.displayName}.")
		}
		if (price == null || price !in Offer.MIN_PRICE..Offer.MAX_PRICE) {
			throw InvalidOfferException("Price must be a whole number from ${Offer.MIN_PRICE} to ${Offer.MAX_PRICE}.")
		}
		if (current.offersOf(email).any { it.system == system && it.goods == goods }) {
			throw InvalidOfferException("You already have an offer on ${goods.name} at ${system.displayName} this turn.")
		}
		if (player.marketPositions.any { it.system == system && it.goods == goods && it.tier == MarketTier.DICTATOR }) {
			throw InvalidOfferException("You're Dictator of ${goods.name} here — you place that offer when it resolves in Trading.")
		}
		return copy(turn = current.copy(offers = current.offers + Offer(email, system, goods, type, price)))
	}

	/**
	 * [email] picks [place] (1 = first) in this turn's turn order (R26). Players pick one at a time in initiative order
	 * (R27), the first one free to take any place, last included; out of turn, or a place that isn't free, is refused.
	 * The last player gets the remaining place without being asked (S01-6, proposed). Each pick is logged, then the
	 * full order (S01-5).
	 */
	fun pickTurnOrderPlace(email: String, place: Int?): Game {
		if (!hasPlayer(email)) {
			throw NotAGameMemberException()
		}
		val current = runningTurn()
		when (current.phase) {
			TurnPhase.BIDDING -> throw InvalidGameOperationException("Ship-jumps hasn't started.")
			TurnPhase.TRADING -> throw PhaseClosedException(TurnPhase.SHIP_JUMPS)
			TurnPhase.SHIP_JUMPS -> Unit
		}
		val picker = current.picker() ?: throw InvalidGameOperationException("The turn order is already set.")
		if (picker != email) {
			throw NotYourTurnException("It's ${corporationOf(picker)}'s turn to choose a place.")
		}
		val order = current.turnOrder()
		if (place == null || place !in 1..order.size || order[place - 1] != null) {
			throw InvalidPlaceException()
		}
		val withPick = current.copy(turnOrderPicks = current.turnOrderPicks + TurnOrderPick(email, place))
		val events = mutableListOf("${corporationOf(email)} chose ${ordinal(place)}")
		val lastPicker = withPick.picker()
		val settled = if (lastPicker != null && withPick.turnOrder().count { it == null } == 1) {
			val remaining = withPick.turnOrder().indexOf(null) + 1
			events += "${corporationOf(lastPicker)} gets ${ordinal(remaining)}, the only place left"
			withPick.copy(turnOrderPicks = withPick.turnOrderPicks + TurnOrderPick(lastPicker, remaining))
		} else {
			withPick
		}
		if (settled.picker() == null) {
			events += "Turn order (for this turn): " + settled.turnOrder().joinToString(" → ") { corporationOf(checkNotNull(it)) }
		}
		return copy(turn = settled, log = log + events.map { LogEntry(current.number, it) })
	}

	/**
	 * [email] jumps their ship [shipName] to [destination] during their slot (R26, S02): instantly, with no fuel, damage
	 * or transit time (R19), arriving in Space until the player chooses where it ends up (S03). Each ship jumps at most
	 * once per turn, and never to the system it's already at (S02-4). The jump is logged (S02-7); jumping the player's
	 * last ship ends their slot (S02-6).
	 */
	fun jumpShip(email: String, shipName: String?, destination: StarSystem?): Game {
		val current = jumpSlotOf(email)
		val player = players.first { it.email == email }
		val ship = player.ships.firstOrNull { it.name == shipName && it.name !in current.jumpedShips }
			?: throw InvalidJumpException("Choose one of your ships that hasn't jumped this turn.")
		if (destination == null) {
			throw InvalidJumpException("Choose a destination system.")
		}
		if (destination == ship.system) {
			throw InvalidJumpException("${ship.name} is already at ${destination.displayName}. Pick another destination.")
		}
		val moved = player.copy(ships = player.ships.map { if (it == ship) it.copy(system = destination, location = ShipLocation.SPACE) else it })
		val jumped = copy(
			players = players.map { if (it.email == email) moved else it },
			turn = current.copy(jumpedShips = current.jumpedShips + ship.name),
			log = log + LogEntry(current.number, "Jump: ${player.corporationName} — ${ship.name} → ${destination.displayName}")
		)
		return if (moved.ships.all { it.name in jumped.runningTurn().jumpedShips }) jumped.endJumpSlot(email) else jumped
	}

	/** [email] ends their jump slot (S02-6): ships they didn't jump stay where they are, and the next player's slot starts. */
	fun finishJumping(email: String): Game {
		jumpSlotOf(email)
		return endJumpSlot(email)
	}

	/** The running turn, once checked that it's [email]'s jump slot (R04: an ordered step out of turn is refused). */
	private fun jumpSlotOf(email: String): Turn {
		if (!hasPlayer(email)) {
			throw NotAGameMemberException()
		}
		val current = runningTurn()
		when (current.phase) {
			TurnPhase.BIDDING -> throw InvalidGameOperationException("Ship-jumps hasn't started.")
			TurnPhase.TRADING -> throw PhaseClosedException(TurnPhase.SHIP_JUMPS)
			TurnPhase.SHIP_JUMPS -> Unit
		}
		if (current.picker() != null) {
			throw InvalidGameOperationException("The turn order isn't set yet.")
		}
		// Only a game saved before initiative existed (E03-S05) can be in Ship-jumps with nobody to jump.
		val jumper = current.jumper() ?: throw InvalidGameOperationException("Nobody is jumping now.")
		if (jumper != email) {
			throw NotYourTurnException("It's ${corporationOf(jumper)}'s turn to jump.")
		}
		return current
	}

	/** Ends [email]'s slot; after the last one the phase moves to Trading (S02-8). */
	private fun endJumpSlot(email: String): Game {
		val current = runningTurn()
		val ended = copy(turn = current.copy(doneJumping = current.doneJumping + email))
		return if (ended.runningTurn().jumper() == null) ended.copy(turn = ended.runningTurn().copy(phase = TurnPhase.TRADING)) else ended
	}

	/**
	 * Bidding → Ship-jumps (R27): every player pays their bid, win or lose, and a player who never bid counts as
	 * bidding 0 and still rolls (Q07, proposed). The order is public and logged without amounts (R29); the bids stay
	 * secret on the turn.
	 */
	private fun closeBidding(current: Turn, dice: Dice): Game {
		val bids = players.associate { it.email to (current.bidOf(it.email)?.amount ?: 0) }
		val order = initiativeOrder(players.map { it.email }, bids, dice)
		val names = order.joinToString(" → ") { corporationOf(it) }
		return copy(
			players = players.map { it.pay(bids.getValue(it.email)) },
			turn = current.copy(phase = TurnPhase.SHIP_JUMPS, doneBidding = emptyList(), initiative = order),
			log = log + LogEntry(current.number, "Initiative order (bid + d2): $names")
		)
	}

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

		/** A new game waiting in the lobby with its creator as the only player. */
		fun lobby(id: String, turns: Int, creator: Player) = Game(id, turns, GameStatus.LOBBY, creator.email, listOf(creator), null, emptyList())
	}
}

/** 1st, 2nd, 3rd, 4th … for a place in the turn order (players are at most six, so no 11th–13th). */
private fun ordinal(place: Int): String = place.toString() + when (place) {
	1 -> "st"
	2 -> "nd"
	3 -> "rd"
	else -> "th"
}
