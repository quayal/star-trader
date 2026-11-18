package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import dev.adriangrzebyk.trader.game.battle.BattleOrder
import dev.adriangrzebyk.trader.game.battle.BattleShip
import dev.adriangrzebyk.trader.game.battle.Interception
import dev.adriangrzebyk.trader.game.battle.InterceptionAnswer
import dev.adriangrzebyk.trader.game.battle.InvalidBattleException
import dev.adriangrzebyk.trader.game.battle.Salvo
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import java.util.Locale

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
	 * The log entries after the first [count], for a reader who already has those: the log only ever grows at its end. The
	 * whole log when [count] isn't a number of entries this log has, so a reader who is out of step starts over.
	 */
	fun logAfter(count: Int): List<LogEntry> = if (count in 0..log.size) log.drop(count) else log

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
	 * Moves to the next phase (R44): Bidding → Ship-jumps → Trading → Investment → the next turn's Bidding. Closing
	 * Bidding charges the bids and rolls the initiative order with [dice] (R27); leaving Trading starts Investment
	 * ([endTrading]) and leaving Investment ends the turn ([endTurn]). No
	 * clock calls this (D09): a phase ends only when its players are through, and the caller runs it inside
	 * [GameStore.update] so it happens once.
	 */
	fun advancePhase(dice: Dice): Game {
		val current = runningTurn()
		return when (current.phase) {
			TurnPhase.BIDDING -> closeBidding(current, dice)
			TurnPhase.SHIP_JUMPS -> copy(turn = current.toTrading())
			TurnPhase.TRADING -> endTrading(current)
			TurnPhase.INVESTMENT -> endTurn(current)
		}
	}

	/**
	 * [current]'s Trading is over (E05-S07): logged "All goods resolved" in that turn, and Investment starts (R44, E06-S01-1).
	 * A player with no factories has no Collect, so their ships bought last turn leave the yard now (R63).
	 */
	private fun endTrading(current: Turn): Game {
		val investing = copy(turn = current.endTrading(), log = log + LogEntry(current.number, ALL_GOODS_RESOLVED))
		return players.filterNot { it.hasFactories() }.fold(investing) { game, player -> game.withShipsLeavingYard(player.email) }
	}

	/**
	 * This game once [email]'s ships bought in an earlier turn left the yard (R63): right after their Collect, so they never
	 * take that Investment's output (output is phase 6, the ship phase 7). Each departure is logged.
	 */
	private fun withShipsLeavingYard(email: String): Game {
		val current = runningTurn()
		val player = playerOf(email)
		val leaving = player.shipsLeavingYardIn(current.number)
		if (leaving.isEmpty()) {
			return this
		}
		return copy(
			players = replace(player.releaseShipsFromYard(current.number)),
			log = log + leaving.map { LogEntry(current.number, it.leavingYardLog()) }
		)
	}

	/**
	 * Ends [current], whose Investment is over (R44, E06-S01-2). After the last
	 * turn the creator chose (R39, R41) the game is over, keeps its last turn, and the log ends with the winner
	 * (E05-S08-1, S08-5); otherwise the next turn starts with
	 * Bidding, with no offers, so offers that didn't trade expire (S07-2, proposed), and its first log line starts a new
	 * group in the log (S07-3). This is the one place a turn ends and the next starts: anything that takes effect "from
	 * the next turn" (E05-S06's position changes, R43) applies here, before the next turn's Bidding.
	 */
	private fun endTurn(current: Turn): Game {
		if (current.number >= turns) {
			return copy(status = GameStatus.FINISHED, log = log + LogEntry(current.number, gameOverText()))
		}
		val next = current.next()
		return copy(
			turn = next,
			log = log + LogEntry(next.number, "Turn ${next.number} of $turns starts with Bidding; turn ${current.number}'s offers have expired")
		)
	}

	/**
	 * Every player, most money first (R39: only cash counts, so ships and goods are worth nothing); players with the same
	 * money keep the order they joined in. Each player's money is private (R29) until the game is over, so only a finished
	 * game's view may show it (E05-S08-3).
	 */
	fun standings(): List<Player> = players.sortedByDescending { it.money }

	/** The players with the most money (R39); several when they tie, and they share the win (E05-S08-4, proposed). */
	fun winners(): List<Player> = players.filter { it.money == players.maxOf { player -> player.money } }

	/**
	 * S08-5: "Game over: Vega Lines wins with 2,310 HT", or for a shared win "Game over: Vega Lines and Kessler Cargo win
	 * with 2,310 HT".
	 */
	private fun gameOverText(): String {
		val names = winners().map { it.corporationName }
		val who = if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
		val verb = if (names.size == 1) "wins" else "win"
		return "Game over: $who $verb with ${String.format(Locale.US, "%,d", winners().first().money)} HT"
	}

	/**
	 * Records that [email] is done investing (R44, E06-S01-2). Investment closes the moment the last player signals done
	 * (D09), which ends the turn ([endTurn]); a signal after that is refused (A04).
	 */
	fun signalDoneInvesting(email: String): Game {
		val player = playerOf(email)
		val investing = runningTurn()
		investing.requireInvesting()
		requireCollected(player, investing)
		val signalled = copy(turn = runningTurn().signalDoneInvesting(email))
		val current = signalled.runningTurn()
		return if (current.everyoneDoneInvesting(players.map { it.email })) signalled.endTurn(current) else signalled
	}

	/**
	 * [email] confirms where the output of their factories goes (R47, E06-S03-5): stored by [Player.collect], the rest lost,
	 * and from then on they may signal done. The public log says what each factory's output came to (R50, E06-S07-1). Only in Investment, only for a player with factories, and once; a player with
	 * none has nothing to collect (S03-6).
	 */
	fun collectOutput(email: String, placements: List<OutputPlacement>): Game {
		val player = playerOf(email)
		val investing = runningTurn()
		investing.requireInvesting()
		if (!player.hasFactories()) {
			throw InvalidGameOperationException("You have no factories to collect from.")
		}
		val collected = player.collect(placements)
		return copy(
			players = replace(collected),
			turn = investing.collectedBy(email),
			log = log + collected.collectLog(player).map { LogEntry(investing.number, it) }
		).withShipsLeavingYard(email)
	}

	/**
	 * [email] buys [blocks] warehouse blocks at [system] (R45, E06-S04): [Player.buyWarehouseBlocks] checks and applies it,
	 * and the public log says who added how many where, without the price (R50). Only in Investment and, for a player with
	 * factories, after they collected (S03-5), so the new blocks never catch this turn's output (R48). Done is a readiness
	 * flag (D09): a player who signalled done may still invest until everyone has.
	 */
	fun buyWarehouseBlocks(email: String, system: StarSystem, blocks: Int): Game =
		withInvestment(email, "added $blocks warehouse ${if (blocks == 1) "block" else "blocks"} at ${system.displayName}") {
			it.buyWarehouseBlocks(system, blocks)
		}

	/**
	 * [email] buys [units] of production of [goods] at [system] (R46, E06-S05): [Player.buyFactoryUnits] checks and applies
	 * it at the game's current price for that good there, and the public log says who added how many of what where, without
	 * the price (R50). Only in Investment and, for a player with factories, after they collected (S03-5); the new units first
	 * produce at the next turn's Collect (R48). A player with no factory when Investment opened had nothing to collect, so
	 * their first factory doesn't make them collect from it, which would block them and have it produce this turn: they are
	 * recorded as collected. Done is a readiness flag (D09).
	 */
	fun buyFactoryUnits(email: String, system: StarSystem, goods: Goods, units: Int): Game {
		val player = playerOf(email)
		val investing = runningTurn()
		investing.requireInvesting()
		requireCollected(player, investing)
		val bought = player.buyFactoryUnits(system, goods, units, prices)
		val amount = if (units == 1) "1 unit" else "$units units"
		val line = "${player.corporationName} added $amount of ${goods.name} production at ${system.displayName}"
		val settled = if (investing.hasCollected(email)) investing else investing.collectedBy(email)
		return copy(players = replace(bought), turn = settled, log = log + LogEntry(investing.number, line))
	}

	/**
	 * [email] buys a ship (R61, E09-S02): [Ship.commission] checks what it's made of, the game that its name isn't taken by
	 * any ship in it, ignoring case (R62), and [Player.buyShip] that they can pay. It waits in the yard (R63). The public
	 * log names the ship and the yard, never the hull or the price (R50, R57). Only in Investment, after their Collect,
	 * like any purchase (R48).
	 */
	fun buyShip(email: String, name: String?, shipyard: StarSystem, hull: Hull, modules: List<Module>, crewClass: CrewClass): Game {
		val ship = Ship.commission(name, shipyard, hull, modules, crewClass, runningTurn().number)
		if (isShipNameTaken(ship.name)) {
			throw InvalidInvestmentException("There's already a ship called ${ship.name} in this game.")
		}
		return withInvestment(email, "ordered ${ship.name} at the ${shipyard.displayName} shipyard") { it.buyShip(ship) }
	}

	/** The suggested names no ship in this game uses yet, ignoring case (R62), in [Ship.NAMES] order. */
	fun freeShipNames(): List<String> = Ship.NAMES.filterNot { isShipNameTaken(it) }

	/** Whether any player has a ship called [name], ignoring case (R62). */
	private fun isShipNameTaken(name: String): Boolean = players.any { it.hasShipNamed(name) }

	/** [email] fits one more [module] to their ship [shipName] at a shipyard (R65): see [Player.addModule]. Private, not logged. */
	fun addModule(email: String, shipName: String, module: Module): Game = withInvestment(email, null) { it.addModule(shipName, module) }

	/** [email] removes one [module] from their ship [shipName] at a shipyard (R65): see [Player.removeModule]. Private, not logged. */
	fun removeModule(email: String, shipName: String, module: Module): Game = withInvestment(email, null) { it.removeModule(shipName, module) }

	/** [email] replaces the crew of their ship [shipName] with one of [crewClass] (R89): see [Player.replaceCrew]. Private, not logged. */
	fun replaceCrew(email: String, shipName: String, crewClass: CrewClass): Game =
		withInvestment(email, null) { it.replaceCrew(shipName, crewClass) }

	/** [email] repairs all the damage on their ship [shipName] at a shipyard (R79): see [Player.repairShip]. Private, not logged. */
	fun repairShip(email: String, shipName: String): Game = withInvestment(email, null) { it.repairShip(shipName) }

	/**
	 * [email] sells their ship [shipName] (R66): see [Player.sellShip]. The sale is public, without the amount (R29); what
	 * happened to the cargo is private, so isn't logged.
	 */
	fun sellShip(email: String, shipName: String): Game = withInvestment(email, "sold $shipName") { it.sellShip(shipName) }

	/**
	 * This game once [change] applied to [email] in Investment, after their Collect (S03-5), with "{corporation} [event]"
	 * in the public log when there is one.
	 */
	private fun withInvestment(email: String, event: String?, change: (Player) -> Player): Game {
		val player = playerOf(email)
		val investing = runningTurn()
		investing.requireInvesting()
		requireCollected(player, investing)
		val line = event?.let { LogEntry(investing.number, "${player.corporationName} $it") }
		return copy(players = replace(change(player)), log = log + listOfNotNull(line))
	}

	/**
	 * Whether [email] may buy in this Investment now (R45, S03-5): the game is running in Investment and they have no
	 * output left to place. False for anyone who isn't in the game.
	 */
	fun canInvest(email: String): Boolean {
		val current = turn ?: return false
		val player = players.firstOrNull { it.email == email } ?: return false
		return status == GameStatus.RUNNING && current.phase == TurnPhase.INVESTMENT && !(player.hasFactories() && !current.hasCollected(email))
	}

	/** Refuses [player] with factories who hasn't confirmed their collect in [investing]: nothing is done or built before (S03-5). */
	private fun requireCollected(player: Player, investing: Turn) {
		if (player.hasFactories() && !investing.hasCollected(player.email)) {
			throw InvalidGameOperationException("Collect your output first.")
		}
	}

	/**
	 * [email] moves [units] of [goods] [from] one of their ships or warehouse [to] another in the same port (R58):
	 * [Player.moveCargo] checks and applies it. In any phase of a running game and whoever's turn it is, since a move
	 * changes nothing anyone else decides on: what the player holds at that system stays the same (R49). Cargo is private
	 * (R29), so nothing is logged.
	 */
	fun moveCargo(email: String, from: CargoHolder, to: CargoHolder, goods: Goods, units: Int): Game {
		val player = playerOf(email)
		runningTurn()
		return copy(players = replace(player.moveCargo(from, to, goods, units)))
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
	 * Withdraws [email]'s offer on [goods] at [system] while Bidding is open (R59); withdrawing one they don't have
	 * changes nothing. After Bidding closed it's refused as such (A04).
	 */
	fun withdrawOffer(email: String, system: StarSystem, goods: Goods): Game {
		playerOf(email)
		val current = runningTurn()
		current.requireBidding()
		return copy(turn = current.withdrawOffer(email, system, goods))
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
		return copy(turn = settled, log = log + events.map { LogEntry(current.number, it) }).withEmptyJumpSlotsSkipped()
	}

	/**
	 * [email] jumps their ship [shipName] to [destination] during their slot (R26, S02); see [Ship.jumpTo]. Each ship
	 * jumps at most once per turn, and nothing else happens while a ship waits to land (S03). [dice] rolls 2d6 against the
	 * ship's [JumpChance] (R68): a failed jump leaves the ship where it was, with its jump used, and ends the slot when it
	 * was the last ship to jump, as a landing does. Either way the jump and its roll are logged (S02-7).
	 */
	fun jumpShip(email: String, shipName: String?, destination: StarSystem?, dice: Dice): Game {
		val current = jumpSlotOf(email)
		current.refuseDuringFight()
		current.refuseWhileAShipWaitsToLand()
		val player = playerOf(email)
		val ship = player.ship(shipName)?.takeUnless { current.hasJumped(it) }
			?: throw InvalidJumpException("Choose one of your ships that hasn't jumped this turn.")
		if (destination == null) {
			throw InvalidJumpException("Choose a destination system.")
		}
		if (destination == ship.system) {
			return withShipMovedWithin(player, ship, current)
		}
		val roll = dice.twoD6()
		if (!ship.checkedJumpChanceTo(destination).succeeds(roll)) {
			val recorded = current.recordFailedJump(ship)
			val failed = copy(
				turn = recorded,
				log = log + LogEntry(current.number, "Jump: ${player.corporationName} — ${ship.failedJumpLog(destination, roll)}")
			)
			return if (recorded.haveAllJumped(player.jumpableShips())) failed.endJumpSlot(email) else failed
		}
		val moved = player.jumpShip(ship, destination)
		return copy(
			players = replace(moved),
			turn = current.recordJump(ship),
			log = log + LogEntry(current.number, "Jump: ${player.corporationName} — ${checkNotNull(moved.ship(shipName)).jumpLog()} (rolled $roll)")
		).withInterceptionAsked(ship.name, email, destination)
	}

	/** [ship] of [player]'s moved into its own system's Space (R69): no route, so no roll; it can be intercepted (R70). */
	private fun withShipMovedWithin(player: Player, ship: Ship, current: Turn): Game {
		val moved = player.jumpShip(ship, ship.system)
		return copy(
			players = replace(moved),
			turn = current.recordJump(ship),
			log = log + LogEntry(current.number, "Jump: ${player.corporationName} — ${checkNotNull(moved.ship(ship.name)).jumpLog()}")
		).withInterceptionAsked(ship.name, player.email, ship.system)
	}

	/**
	 * This game once the players with ships in Space at [system], other than [ownerEmail], are asked whether to intercept
	 * [shipName], which just arrived there (R70); unchanged when there are none, so the ship lands as before.
	 */
	private fun withInterceptionAsked(shipName: String, ownerEmail: String, system: StarSystem): Game {
		val asked = players.filter { it.email != ownerEmail && it.shipsInSpaceAt(system).isNotEmpty() }.map { it.email }
		if (asked.isEmpty()) {
			return this
		}
		val current = runningTurn()
		val text = "Interception: ${asked.joinToString(", ") { corporationOf(it) }} may try to stop $shipName at ${system.displayName}"
		return copy(
			turn = current.withInterception(Interception(system, shipName, ownerEmail, asked, emptyList())),
			log = log + LogEntry(current.number, text)
		)
	}

	/**
	 * [email] decides which one of their ships in Space tries to intercept the ship that just arrived (R70), or none when
	 * [shipName] is null. Once everyone asked has decided, the tries are rolled with [dice] and logged; a success starts a
	 * battle (R73).
	 */
	fun answerInterception(email: String, shipName: String?, dice: Dice): Game {
		val player = playerOf(email)
		val current = runningTurn()
		val interception = current.interceptionNow()
		val answered = interception.answer(InterceptionAnswer(email, shipName))
		val inSpace = player.shipsInSpaceAt(interception.system).map { it.name }
		if (shipName != null && shipName !in inSpace) {
			throw InvalidBattleException("Choose one of your ships in Space at ${interception.system.displayName}: ${inSpace.joinToString(", ")}.")
		}
		val game = copy(turn = current.withInterception(answered))
		return if (answered.isDecided()) game.withInterceptionRolled(answered, dice) else game
	}

	/**
	 * Every try at [interception] rolled (R70, R71): the ships that tried have used their move (R72); each success holds
	 * the arriving ship in the battle it starts (R73, R74).
	 */
	private fun withInterceptionRolled(interception: Interception, dice: Dice): Game {
		val current = runningTurn()
		val arriving = shipNamed(interception.ship)
		val tries = interception.tries()
		val events = mutableListOf<String>()
		val caught = mutableListOf<BattleInterception>()
		for ((email, name) in tries) {
			val interceptor = checkNotNull(playerOf(email).ship(name))
			val ours = dice.d6()
			val theirs = dice.d6()
			val success = interceptor.interceptTotal(ours) > arriving.interceptTotal(theirs)
			if (success) {
				caught += BattleInterception(arriving.name, interceptor.name)
			}
			events +=
				"Interception: ${interceptor.interceptLog(ours)} vs ${arriving.interceptLog(theirs)}: ${if (success) "intercepted" else "escapes"}"
		}
		if (tries.isEmpty()) {
			events += "Nobody tries to intercept ${interception.ship}"
		}
		val decided = copy(
			turn = current.withInterception(null).recordInterceptors(tries.map { it.second }),
			log = log + events.map { LogEntry(current.number, it) }
		)
		return if (caught.isEmpty()) decided else decided.withBattleStarted(interception.system, caught)
	}

	/** This game with a battle at [system] among every ship in Space there (R73, R80), [interceptions] holding their ships. */
	private fun withBattleStarted(system: StarSystem, interceptions: List<BattleInterception>): Game {
		val current = runningTurn()
		val fighting = players.flatMap { player -> player.shipsInSpaceAt(system).map { BattleShip(it.name, player.email) } }
		return copy(
			turn = current.withBattleStarted(Battle.start(system, fighting, interceptions)),
			log = log + LogEntry(current.number, "Battle at ${system.displayName}: ${fighting.joinToString(", ") { it.name }}")
		)
	}

	/**
	 * [email] gives this round's [orders] to their ships in the battle (R74), secret until every fighting player has; then
	 * all of them resolve together with [dice].
	 */
	fun giveBattleOrders(email: String, orders: List<BattleOrder>, dice: Dice): Game {
		playerOf(email)
		val current = runningTurn()
		val battle = current.battleNow().giveOrders(email, orders)
		orders.filter { it.rockets }.forEach { shipNamed(it.ship).checkCanFireRockets() }
		val game = copy(turn = current.withBattle(battle))
		return if (battle.isRoundOrdered()) game.withRoundFired(battle, dice) else game
	}

	/**
	 * [battle]'s round once every salvo has landed (R74–R77): ships breaking away leave, rocket kills and ships that can't
	 * survive their hits are destroyed, and the rest of the hits wait for their owners to place them. All of it is logged.
	 */
	private fun withRoundFired(battle: Battle, dice: Dice): Game {
		val current = runningTurn()
		val salvos = battle.fireOrders().map { order ->
			val target = order.targetShip()
			Salvo.fire(shipNamed(order.ship), shipNamed(target), order.rockets, battle.isBreakingAway(target), dice)
		}
		val round = battle.fired(salvos, battle.fightingShips().associateWith { shipNamed(it).hitsSurvivable() })
		return copy(
			players = players.map { player -> round.destroyed.fold(player) { owner, ship -> owner.loseShip(ship) } },
			turn = current.withBattleRound(round.battle, round.report),
			log = log + round.log.map { LogEntry(current.number, it) }
		).withBattleMovedOn()
	}

	/**
	 * [email] places the hits each of their ships took this round (R76), [placements] by ship name; a ship that can't
	 * absorb them is destroyed (R77). When nobody has hits left to place, the next round starts or the battle ends.
	 */
	fun placeBattleHits(email: String, placements: Map<String, HitPlacement>): Game {
		val player = playerOf(email)
		val current = runningTurn()
		val battle = current.battleNow()
		val pending = battle.hitsToPlace(email, placements.keys)
		val hit = pending.fold(player) { owner, hits -> owner.takeHits(hits.ship, hits.hits, placements.getValue(hits.ship)) }
		val destroyed = pending.map { it.ship }.filter { hit.ship(it) == null }.toSet()
		return copy(
			players = replace(hit),
			turn = current.withHitsPlaced(battle.hitsPlaced(email, destroyed), placements.keys, destroyed),
			log = log + destroyed.map { LogEntry(current.number, "$it is destroyed") }
		).withBattleMovedOn()
	}

	/**
	 * [email] places each of their surviving ships from the battle at Port, Planet or Space (R77), [locations] by ship
	 * name; each is logged as a landing. Once everyone has, the battle is over and the jumps go on.
	 */
	fun landAfterBattle(email: String, locations: Map<String, ShipLocation>): Game {
		val player = playerOf(email)
		val current = runningTurn()
		val battle = current.battleNow()
		val landed = battle.landedBy(email, locations.keys)
		val survivors = battle.survivorsOf(email)
		val placed = locations.entries.fold(player) { owner, (ship, location) -> owner.landShip(ship, location) }
		val events = survivors.map { "Landing: ${player.corporationName} — ${checkNotNull(placed.ship(it)).landingLog()}" }
		return copy(
			players = replace(placed),
			turn = current.withBattle(landed),
			log = log + events.map { LogEntry(current.number, it) }
		).withBattleMovedOn()
	}

	/**
	 * This game once its battle moved on: when it's over, that is logged; once every survivor has been placed, the
	 * battle is gone, the ship that arrived has landed or been destroyed, and the jump slot goes on or ends (S02-6).
	 */
	private fun withBattleMovedOn(): Game {
		val current = runningTurn()
		val battle = checkNotNull(current.battle)
		val events = if (battle.isJustOver()) {
			listOf(
				LogEntry(current.number, "The battle at ${battle.system.displayName} is over")
			)
		} else {
			emptyList()
		}
		if (!battle.isFinished()) {
			return copy(log = log + events)
		}
		val after = current.withBattle(null).landed()
		val game = copy(turn = after, log = log + events)
		val jumper = after.jumper() ?: return game
		return if (after.haveAllJumped(playerOf(jumper).jumpableShips())) game.endJumpSlot(jumper) else game
	}

	/** The ship called [name], whoever owns it. */
	private fun shipNamed(name: String): Ship = players.firstNotNullOf { it.ship(name) }

	/**
	 * [email] chooses where the ship that just jumped ends up (R30, S03). That finishes the jump; the landing is logged
	 * (S03-3), and landing the player's last ship ends their slot (S02-6, S03-4). Staying in Space next to other players'
	 * ships starts a battle instead (R80); the slot goes on once it's over.
	 */
	fun landShip(email: String, location: ShipLocation?): Game {
		val current = jumpSlotOf(email)
		current.refuseDuringFight()
		val shipName = current.shipWaitingToLand()
		if (location == null) {
			throw InvalidLandingException()
		}
		val landed = playerOf(email).landShip(shipName, location)
		val ship = checkNotNull(landed.ship(shipName))
		val game = copy(
			players = replace(landed),
			turn = current.landed(),
			log = log + LogEntry(current.number, "Landing: ${landed.corporationName} — ${ship.landingLog()}")
		)
		// R80: staying in Space where other players' ships are starts a battle, with nobody intercepted.
		if (location == ShipLocation.SPACE && players.any { it.email != email && it.shipsInSpaceAt(ship.system).isNotEmpty() }) {
			return game.withBattleStarted(ship.system, emptyList())
		}
		return if (current.haveAllJumped(landed.jumpableShips())) game.endJumpSlot(email) else game
	}

	/** [email] ends their jump slot (S02-6): ships they didn't jump stay where they are, and the next player's slot starts. */
	fun finishJumping(email: String): Game {
		val current = jumpSlotOf(email)
		current.refuseDuringFight()
		current.refuseWhileAShipWaitsToLand()
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
	 * [loads] and their warehouse there as [warehouseUnits] (E05-S04, R49): [Player.trade] checks the amount against their
	 * max (R34, R12, R10) and the split (R42), and
	 * moves their money and cargo. The trade is public and logged (S04-6): "Vega Lines could buy up to 12 u: bought 10 u
	 * at 13 HT each, paid 130 HT". A trade of 6 units or more then rolls 2d6 from [dice] for its player's market position
	 * there (R43, E05-S06), logged right after it. Then the counter reaches the next offer.
	 */
	fun trade(email: String, amount: Int?, loads: List<ShipLoad>, dice: Dice, warehouseUnits: Int = 0): Game {
		val current = traderTurnOf(email)
		val awaited = current.tradeAwaited()
		val trader = playerOf(email).trade(awaited.offer, awaited.marketMax, amount, loads, warehouseUnits)
		val units = checkNotNull(amount)
		val good = checkNotNull(current.resolution).good
		val positionRoll = positionRollFor(trader, good, units, dice)
		return copy(players = replace(trader)).withPositionRolled(email, good, positionRoll).withTradeRecorded(current, units, positionRoll)
	}

	/**
	 * The position roll [trader] makes after trading [units] of [good] (R43, E05-S06-2), or null when they traded fewer
	 * than 6 or are its Dictator already (S06-3: nothing to gain). A roll at most [units] moves them one tier up; becoming
	 * Dictator demotes the Dictator there to Wholesaler (S06-3). Both hold at once (S06-5).
	 */
	private fun positionRollFor(trader: Player, good: MarketGood, units: Int, dice: Dice): PositionRoll? {
		if (units < PositionRoll.MIN_UNITS || !trader.canRiseAt(good)) {
			return null
		}
		val roll = dice.twoD6()
		if (roll > units) {
			return PositionRoll(roll, null, emptyList())
		}
		val tier = trader.promotionAt(good)
		val demoted = if (tier == MarketTier.DICTATOR) {
			players.filter { it.email != trader.email && it.isDictatorOf(good) }.map { it.email }
		} else {
			emptyList()
		}
		return PositionRoll(roll, tier, demoted)
	}

	/** This game once [positionRoll], made by [email] for [good], moved positions; unchanged when it didn't. */
	private fun withPositionRolled(email: String, good: MarketGood, positionRoll: PositionRoll?): Game {
		if (positionRoll?.promotedTo == null) {
			return this
		}
		return copy(
			players = players.map {
				when {
					it.email == email -> it.promotedAt(good)
					it.email in positionRoll.demoted -> it.demotedAt(good)
					else -> it
				}
			}
		)
	}

	/**
	 * [email], whose offer the counter has reached, acknowledges that they can trade nothing (E05-S04-5): no money for a
	 * unit (R12), or none of the good in their ships in port or warehouse there (R10, R49). Refused while they can trade a unit: then they
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
		return withTradeRecorded(current, 0, null)
	}

	private fun withTradeRecorded(current: Turn, units: Int, positionRoll: PositionRoll?): Game =
		withResolutionMoved(current.recordTrade(units, positionRoll), current.resolution, emptyList())

	/**
	 * [email], asked in the wholesale step (R37, E05-S05-4), buys or sells ([type]) [amount] units at the good's new price,
	 * split across their ships in port there as [loads] and their warehouse there as [warehouseUnits]: settled like any trade
	 * ([Player.trade]), with at most 5 units,
	 * capped by money and cargo (R12, R10, R42). It doesn't move the price. Logged (S05-5): "Kessler Cargo (Wholesaler)
	 * bought 3 u at 10 HT each, paid 30 HT".
	 */
	fun wholesale(email: String, type: OfferType?, amount: Int?, loads: List<ShipLoad>, warehouseUnits: Int = 0): Game {
		val current = wholesalerTurnOf(email)
		if (type == null) {
			throw InvalidTradeException("Choose Buy or Sell, or pass.")
		}
		val offer = checkNotNull(current.resolution).wholesaleOffer(type)
		val trader = playerOf(email).trade(offer, Wholesale.MAX_UNITS, amount, loads, warehouseUnits)
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
		return if (after.isEveryGoodResolved()) moved.endTrading(after) else moved
	}

	/**
	 * The public log lines of what [resolution] did in turn [turn] since [before] (null for a good that just started
	 * resolving), as it happens (S03-6, D07: no dice arithmetic): "Mu Herculis ISO: market +3 → buyers first" when the
	 * counter starts, each trade when it's made ("Vega Lines could buy up to 12 u: bought 10 u at 13 HT each, paid 130
	 * HT") and its position roll, if any (E05-S06-4), then once trading is over "No one else can trade at Mu Herculis ISO" and the price change, "ISO price at Mu
	 * Herculis: 11 → 10 HT" (S05-2), then each wholesale answer (S05-5). None while the Dictator is still asked.
	 */
	private fun resolutionLog(turn: Int, before: Resolution?, resolution: Resolution): List<LogEntry> {
		val market = resolution.marketText() ?: return emptyList()
		val label = resolution.good.label()
		val started = listOfNotNull("$label: $market".takeIf { before?.startingCounter == null })
		val trades = resolution.trades.drop(before?.trades?.size ?: 0).flatMap { trade ->
			listOfNotNull("${corporationOf(trade.offer.playerEmail)} ${trade.terms()}", positionRollText(trade, resolution.good))
		}
		val ended = if (resolution.isTradingOver() && before?.isTradingOver() != true) {
			listOf("${resolution.endText()} at $label", checkNotNull(resolution.priceText()))
		} else {
			emptyList()
		}
		val wholesales = resolution.wholesales.drop(before?.wholesales?.size ?: 0)
			.map { "${corporationOf(it.playerEmail)} (${resolution.wholesaleRole(it.playerEmail)}) ${it.terms()}" }
		return (started + trades + ended + wholesales).map { LogEntry(turn, it) }
	}

	/**
	 * The public log line of [trade]'s position roll, if it made one (E05-S06-4): "Vega Lines traded 7 u — rolled 5: now
	 * Wholesaler of ISO at Mu Herculis", "… rolled 9: no change", or, becoming Dictator, "…; Altair Trade Co. is demoted
	 * to Wholesaler".
	 */
	private fun positionRollText(trade: Trade, good: MarketGood): String? = trade.positionRoll?.let { roll ->
		"${corporationOf(trade.offer.playerEmail)} traded ${trade.units} u — ${roll.outcome(good)}" +
			roll.demoted.joinToString("") { "; ${corporationOf(it)} is demoted to Wholesaler" }
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

	private fun endJumpSlot(email: String): Game = copy(turn = runningTurn().endJumpSlot(email)).withEmptyJumpSlotsSkipped()

	/**
	 * This game once every jump slot that has nothing to jump has ended (R64): a player whose ships are all in the yard, or
	 * who has none, can't act, so the turn doesn't wait for them. Each is logged.
	 */
	private fun withEmptyJumpSlotsSkipped(): Game {
		val current = turn ?: return this
		val jumper = current.jumper() ?: return this
		if (playerOf(jumper).jumpableShips().isNotEmpty()) {
			return this
		}
		return copy(
			turn = current.endJumpSlot(jumper),
			log = log + LogEntry(current.number, "${corporationOf(jumper)} has no ship to jump")
		).withEmptyJumpSlotsSkipped()
	}

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

		/** S07-1: the log line once a turn's last good has resolved. */
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
