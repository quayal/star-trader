package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentOffer
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.InvalidAgentException
import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import dev.adriangrzebyk.trader.game.battle.BattleOrder
import dev.adriangrzebyk.trader.game.battle.BattleShip
import dev.adriangrzebyk.trader.game.battle.BattleSummary
import dev.adriangrzebyk.trader.game.battle.Interception
import dev.adriangrzebyk.trader.game.battle.InterceptionAnswer
import dev.adriangrzebyk.trader.game.battle.InvalidBattleException
import dev.adriangrzebyk.trader.game.battle.Salvo
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import dev.adriangrzebyk.trader.game.news.ActiveTipOff
import dev.adriangrzebyk.trader.game.news.Expedition
import dev.adriangrzebyk.trader.game.news.News
import dev.adriangrzebyk.trader.game.news.NewsEvent
import dev.adriangrzebyk.trader.game.news.OpportunityTerms
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TakeOrder
import dev.adriangrzebyk.trader.game.news.TipOff
import dev.adriangrzebyk.trader.game.news.TipOffView
import dev.adriangrzebyk.trader.game.news.TurnConditions
import dev.adriangrzebyk.trader.game.sabotage.InvalidSabotageException
import dev.adriangrzebyk.trader.game.sabotage.Sabotage
import dev.adriangrzebyk.trader.game.sabotage.SabotageTable
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget
import dev.adriangrzebyk.trader.game.smuggling.LandingOdds
import dev.adriangrzebyk.trader.game.smuggling.LandingOutcome
import dev.adriangrzebyk.trader.game.smuggling.LandingRoll

/**
 * [turn] is null until the game starts; [prices] are each good's price at each system in this game, which Trading moves
 * (R35); [log] holds the public events, oldest first, each with its turn. [deadAgents] are the agents killed in this game,
 * which never return to the pool (R107). [joinRequests] are the players asking to join while the game is in its lobby, in the
 * order they asked, for the creator to accept or decline (R120). [news] holds the tip-offs (R109–R111) and what this turn's events changed (R115).
 */
data class Game(
	val id: String,
	val turns: Int,
	val status: GameStatus,
	val creatorEmail: String,
	val players: List<Player>,
	val joinRequests: List<Player>,
	val turn: Turn?,
	val prices: MarketPrices,
	val log: List<LogEntry>,
	val deadAgents: List<Agent>,
	val news: News = News.NONE,
	val supplyDemandShifts: SupplyDemandShifts = SupplyDemandShifts.NONE
) {

	fun addPlayer(player: Player): Game {
		checkJoinable(player.email)
		return copy(players = players + player)
	}

	fun hasPlayer(email: String): Boolean = players.any { it.email == email }

	/** R120: [player] asks to join; the creator decides. Only in the lobby, not when full, in the game already, or asked. */
	fun requestToJoin(player: Player): Game {
		checkJoinable(player.email)
		if (hasAskedToJoin(player.email)) {
			throw InvalidGameOperationException("You have already asked to join this game")
		}
		return copy(joinRequests = joinRequests + player)
	}

	/** R120: the player with [email] no longer asks to join; nothing changes when they hadn't asked. */
	fun withdrawJoinRequest(email: String): Game = copy(joinRequests = joinRequests.filterNot { it.email == email })

	/** R120: the creator lets the player who asked, [email], in, with [addPlayer]'s checks. */
	fun acceptJoinRequest(callerEmail: String, email: String): Game {
		val requester = joinRequestOf(callerEmail, email)
		return withdrawJoinRequest(email).addPlayer(requester)
	}

	/** R120: the creator turns the player who asked, [email], away. */
	fun declineJoinRequest(callerEmail: String, email: String): Game {
		joinRequestOf(callerEmail, email)
		return withdrawJoinRequest(email)
	}

	/** The player who created the game: always one of its players. */
	fun creator(): Player = playerOf(creatorEmail)

	fun hasAskedToJoin(email: String): Boolean = joinRequests.any { it.email == email }

	/** R119: where the player with [email] stands in this game's lobby. */
	fun lobbyStandingOf(email: String): LobbyStanding = when {
		hasPlayer(email) -> LobbyStanding.IN
		hasAskedToJoin(email) -> LobbyStanding.ASKED
		else -> LobbyStanding.NONE
	}

	/** The player [email] whose request the creator, [callerEmail], decides on. */
	private fun joinRequestOf(callerEmail: String, email: String): Player {
		if (callerEmail != creatorEmail) {
			throw NotTheCreatorException("Only the creator can accept or decline a request to join")
		}
		return joinRequests.firstOrNull { it.email == email }
			?: throw InvalidGameOperationException("That request to join is no longer waiting")
	}

	private fun checkJoinable(email: String) {
		if (status != GameStatus.LOBBY) {
			throw InvalidGameOperationException("This game has already started")
		}
		if (players.size >= MAX_PLAYERS) {
			throw InvalidGameOperationException("This game is full")
		}
		if (hasPlayer(email)) {
			throw InvalidGameOperationException("This player is already in the game")
		}
	}

	/**
	 * The log entries after the first [count], for a reader who already has those: the log only ever grows at its end. The
	 * whole log when [count] isn't a number of entries this log has, so a reader who is out of step starts over.
	 */
	fun logAfter(count: Int): List<LogEntry> = if (count in 0..log.size) log.drop(count) else log

	/**
	 * Closes joining and sets every player up (R40, R41): 300 HT and two empty ships at the Ports of two
	 * different systems from [picker], and 3 + d3 connection points from [dice] to place in turn 1's Bidding (R82), each
	 * roll logged; then turn 1 Bidding. Only the creator may start, once, with 2+ players.
	 */
	fun start(callerEmail: String, picker: StartingSystemPicker, dice: Dice): Game {
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
			val roll = dice.d3()
			player.startWith(STARTING_MONEY, ships, Connections.BASE_STARTING_POINTS + roll) to roll
		}
		return copy(
			status = GameStatus.RUNNING,
			players = setUp.map { it.first },
			joinRequests = emptyList(), // R120: joining closes at the start
			turn = Turn.bidding(1),
			log = log + LogEntry.event(1, "Game started: ${setUp.size} players, $turns turns") +
				LogEntry.event(1, "Starting positions: " + setUp.joinToString("; ") { it.first.startingPositions() }) +
				setUp.map { (player, roll) -> LogEntry.event(1, player.connectionPointsRolledLog(roll)) }
		)
	}

	/**
	 * Moves to the next phase (R44): Bidding → Initiative → News → Jumps → Trading → Opportunity → Investment → the next
	 * turn's Bidding. Leaving Initiative fills the free places in initiative order and opens News with [dice]. Closing
	 * Bidding charges the bids and rolls the initiative order with [dice] (R27); leaving Trading starts Investment
	 * ([endTrading]) and leaving Investment ends the turn ([endTurn]). No
	 * clock calls this (D09): a phase ends only when its players are through, and the caller runs it inside
	 * [GameStore.update] so it happens once.
	 */
	fun advancePhase(dice: Dice): Game {
		val current = runningTurn()
		return when (current.phase) {
			TurnPhase.BIDDING -> closeBidding(current, dice)
			TurnPhase.INITIATIVE -> copy(turn = current.withPlacesFilled()).openNews(dice)
			TurnPhase.NEWS -> openJumps(current)
			TurnPhase.JUMPS -> copy(turn = current.toTrading())
			TurnPhase.TRADING -> endTrading(current)
			TurnPhase.OPPORTUNITY -> openInvestment(current)
			TurnPhase.INVESTMENT -> endTurn(current)
		}
	}

	/** [current]'s Trading is over (E05-S07): logged "All goods resolved" in that turn, and Opportunity starts (R44, R102). */
	private fun endTrading(current: Turn): Game =
		copy(turn = current.endTrading(), log = log + LogEntry.event(current.number, ALL_GOODS_RESOLVED))

	/**
	 * [current]'s Opportunity is over (R102): Investment starts, and every player's ships bought in an earlier turn leave the
	 * yard (R63), so they never took that Opportunity's output.
	 */
	private fun openInvestment(current: Turn): Game =
		players.fold(copy(turn = current.toInvestment())) { game, player -> game.withShipsLeavingYard(player.email) }

	/** This game once [email]'s ships bought in an earlier turn left the yard (R63). Each departure is logged. */
	private fun withShipsLeavingYard(email: String): Game {
		val current = runningTurn()
		val player = playerOf(email)
		val leaving = player.shipsLeavingYardIn(current.number)
		if (leaving.isEmpty()) {
			return this
		}
		return copy(
			players = replace(player.releaseShipsFromYard(current.number)),
			log = log + leaving.map { LogEntry.event(current.number, it.leavingYardLog()) }
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
		val last = current.number >= turns
		val loans = withUnpaidInterestPenalized(current).withLoansCollected(current.number)
		val settled = (if (last) loans else loans.withReputationRecovered(current.number)).withReputationBonusPaid(current.number)
		return if (last) settled.finished(current.number) else settled.withNextTurn(current)
	}

	/**
	 * [current]'s Investment closing (R92): every player whose loan interest was due and unpaid loses 4 reputation and owes
	 * it on top of their loan, each logged without the amount.
	 */
	private fun withUnpaidInterestPenalized(current: Turn): Game {
		val unpaid = players.filter { it.owesInterestIn(current.number) && !current.hasPaidInterest(it.email) }
		return unpaid.fold(this) { game, player ->
			val penalized = player.withUnpaidInterest()
			game.withPlayerChanged(penalized.player, penalized.lines, current.number)
		}
	}

	/** This game with [changed] in place of its player, and [lines] logged in turn [number]. */
	private fun withPlayerChanged(changed: Player, lines: List<String>, number: Int): Game =
		copy(players = replace(changed), log = log + lines.map { LogEntry.event(number, it) })

	/** Turn [number]'s Investment closing (R94): every loan at the end of its term is collected, any seizure logged. */
	private fun withLoansCollected(number: Int): Game = players.filter { it.loan?.fallsDueIn(number) == true }.fold(this) { game, player ->
		val (collected, collection) = player.collectLoan(prices)
		game.withPlayerChanged(collected, listOfNotNull(collection.logText(player.corporationName)), number)
	}

	/** This game over after turn [number], the log ending with the winner (E05-S08-1, S08-5). */
	private fun finished(number: Int): Game {
		val text = corporationNames().winText(winners().map { it.email }, winners().first().finalMoney())
		return copy(status = GameStatus.FINISHED, log = log + LogEntry.event(number, text))
	}

	/**
	 * This game in the turn after [current], starting with Bidding (S07-2). No log line says so: the log groups its
	 * lines by turn, and the top bar shows the turn (creator, 2026-11-19).
	 */
	private fun withNextTurn(current: Turn): Game {
		val next = current.next()
		return copy(turn = next, players = players.map { it.inTurn(next.number) })
	}

	/** Turn [number]'s end (R86, E13-S01-2): every player whose reputation is from 1 to 17 recovers 3, each logged. */
	private fun withReputationRecovered(number: Int): Game {
		val recovering = players.filter { it.reputationRecovery() > 0 }
		val recovered = recovering.map { it.withReputationChanged(it.reputationRecovery()) to it.reputationRecovery() }
		return copy(
			players = players.map { player -> recovered.firstOrNull { it.first.email == player.email }?.first ?: player },
			log = log + recovered.map { (player, delta) -> LogEntry.event(number, player.reputationLog(delta, "recovering")) }
		)
	}

	/**
	 * Turn [number]'s end (R88, E13-S01-3): every player with reputation 25 or more is paid its bonus, the last turn
	 * included, before the winner is decided. One public line names who was paid; the amounts are private (R29).
	 */
	private fun withReputationBonusPaid(number: Int): Game {
		val paid = players.filter { it.reputationBonus() > 0 }
		if (paid.isEmpty()) {
			return this
		}
		return copy(
			players = players.map { it.receive(it.reputationBonus(), "Reputation bonus") },
			log = log + LogEntry.event(number, "Reputation bonus: ${paid.joinToString(", ") { it.corporationName }}")
		)
	}

	/**
	 * Every player, most final money first (R39, R95: cash less an open loan, so ships and goods are worth nothing); players
	 * with the same money keep the order they joined in. Each player's money is private (R29) until the game is over, so only
	 * a finished game's view may show it (E05-S08-3).
	 */
	fun standings(): List<Player> = players.sortedByDescending { it.finalMoney() }

	/** The players with the most final money (R39, R95); several when they tie, and they share the win (E05-S08-4, proposed). */
	fun winners(): List<Player> = players.filter { it.finalMoney() == players.maxOf { player -> player.finalMoney() } }

	/** The result once the game is over (E05-S08-2, R39: then everyone's money is public), and null until then (R29). */
	fun result(): GameResult? = if (status == GameStatus.FINISHED) GameResult(standings(), winners()) else null

	/**
	 * Records that [email] is done investing (R44, E06-S01-2). Investment closes the moment the last player signals done
	 * (D09), which ends the turn ([endTurn]); a signal after that is refused (A04).
	 */
	fun signalDoneInvesting(email: String): Game {
		playerOf(email)
		val signalled = copy(turn = investing().signalDoneInvesting(email))
		val current = signalled.runningTurn()
		return if (current.everyoneDoneInvesting(players.map { it.email })) signalled.endTurn(current) else signalled
	}

	/**
	 * [email] confirms where the output of their factories goes (R47, R102): stored by [Player.collect], the rest lost, and
	 * from then on they may act in their Opportunity turn. The public log says what each factory's output came to (R50,
	 * E06-S07-1). Only on their own Opportunity turn, only for a player with factories, and once; a player with none has
	 * nothing to collect (S03-6).
	 */
	fun collectOutput(email: String, placements: List<OutputPlacement>): Game {
		val player = playerOf(email)
		runningTurn().requireOpportunity()
		if (!player.hasFactories()) {
			throw InvalidGameOperationException("You have no factories to collect from.")
		}
		val current = opportunityTurnOf(email)
		val collected = player.collect(placements)
		return copy(
			players = replace(collected),
			turn = current.collectedBy(email),
			log = log + collected.collectLog(player).map { LogEntry.event(current.number, it) }
		)
	}

	/** The running Opportunity, once checked that it's [email]'s turn in it (R102); anyone else is refused, naming whose it is. */
	private fun opportunityTurnOf(email: String): Turn {
		runningTurn().requireOpportunity()
		return turnAwaiting(email, Turn::currentOpportunist, Turn::opportunistWaitingText)
	}

	/** The running Opportunity on [email]'s turn, once they have collected if they have factories (R102: Collect comes first). */
	private fun opportunityAfterCollect(email: String): Turn {
		val current = opportunityTurnOf(email)
		if (playerOf(email).hasFactories() && !current.hasCollected(email)) {
			throw InvalidGameOperationException("Collect your output first.")
		}
		return current
	}

	/**
	 * [email] is done with opportunities (R102): their Collect must be confirmed and any warehouse choice of their sabotage
	 * made; then the next player in the turn order is on. After the last, Investment opens once no choice owed out of turn
	 * is open (R102).
	 */
	fun finishOpportunities(email: String): Game {
		val current = opportunityAfterCollect(email)
		current.refuseDoneWhileChoosing(email)
		if (playerOf(email).expeditions.any { it.isDueIn(current.number) }) {
			throw InvalidGameOperationException("Bring your expedition back first: choose the system it returns to.")
		}
		return withTurn(current.endOpportunityTurn(email)).withOpportunityClosedIfOver()
	}

	// ── Opportunities (R111–R114): taken on the player's Opportunity turn, once they've learned the tip-off ──

	/**
	 * [email] takes the opportunity in [slot] with [order] (R111–R114), as its kind asks; [dice] for the expedition's roll.
	 * A military-ship card with no hull in the order fits modules alone (O13's "or modules").
	 */
	fun takeOpportunity(email: String, slot: Int?, order: TakeOrder, dice: Dice): Game =
		when (val terms = news.inSlot(slot).tipOff.opportunity) {
			is OpportunityTerms.IllegalGoods -> buyIllegalGoods(email, slot, order.ship, order.units)
			is OpportunityTerms.Furs -> buyFurs(email, slot, order.ship, order.units)
			is OpportunityTerms.MilitaryShip ->
				if (order.hull == null && terms.hullOptional) {
					buyMilitaryModules(email, slot, order.ship, order.modules, order.removed)
				} else {
					buyMilitaryShip(email, slot, order.name, order.hull, order.modules, order.crewClass)
				}
			is OpportunityTerms.MilitaryModules -> buyMilitaryModules(email, slot, order.ship, order.modules, order.removed)
			is OpportunityTerms.Delivery -> acceptDelivery(email, slot, order.ship)
			OpportunityTerms.Expedition -> sendExpedition(email, slot, order.ship, dice)
			null -> throw InvalidGameOperationException("An event can't be taken.")
		}

	/** The opportunity in [slot] [email] may take now, on their Opportunity turn after Collect (R111). */
	private fun opportunityToTake(email: String, slot: Int?): ActiveTipOff {
		opportunityAfterCollect(email)
		return news.opportunityFor(slot, email) { corporationOf(it) }
	}

	/**
	 * This game with [tipOff]'s opportunity taken by [email] (R111): a legal one announced with the taker, an illegal one
	 * secret. [changed] is the player after taking it.
	 */
	private fun withOpportunityTaken(email: String, tipOff: ActiveTipOff, changed: Player): Game {
		val terms = checkNotNull(tipOff.tipOff.opportunity)
		val line = "${changed.corporationName} took the opportunity to ${terms.description()}.".takeIf { terms.legal }
		return copy(
			players = replace(changed),
			news = news.withTipOff(tipOff.takenBy(email))
		).withLogged(runningTurn().number, listOfNotNull(line))
	}

	/** [email] buys [units] (1 to 10) of the illegal goods in [slot] into their ship [shipName] on the buy system's Planet (R112). */
	fun buyIllegalGoods(email: String, slot: Int?, shipName: String?, units: Int?): Game {
		val tipOff = opportunityToTake(email, slot)
		val terms =
			tipOff.tipOff.opportunity as? OpportunityTerms.IllegalGoods ?: throw InvalidGameOperationException("This opportunity isn't for goods.")
		val player = playerOf(email)
		val ship = player.ship(shipName)
		if (ship?.isAt(terms.buyAt, ShipLocation.PLANET) != true) {
			throw InvalidGameOperationException("You need a ship on ${terms.buyAt.displayName}'s Planet.")
		}
		val count = checkUnits(units)
		return withOpportunityTaken(email, tipOff, player.buySpecial(shipName, SpecialLot(terms.goods, count, terms.sellAt), terms.price))
	}

	/** [email] buys [units] (1 to 10) furs into their ship [shipName] in the buy system's Port (R112, O5). */
	fun buyFurs(email: String, slot: Int?, shipName: String?, units: Int?): Game {
		val tipOff = opportunityToTake(email, slot)
		val terms = tipOff.tipOff.opportunity as? OpportunityTerms.Furs ?: throw InvalidGameOperationException("This opportunity isn't for furs.")
		val player = playerOf(email)
		if (player.ship(shipName)?.isInPortAt(terms.buyAt) != true) {
			throw InvalidGameOperationException("You need a ship in ${terms.buyAt.displayName}'s Port.")
		}
		return withOpportunityTaken(
			email,
			tipOff,
			player.buySpecial(shipName, SpecialLot(SpecialGoods.FURS, checkUnits(units), terms.sellAt), terms.price)
		)
	}

	private fun checkUnits(units: Int?): Int {
		if (units == null || units !in 1..OpportunityTerms.MAX_UNITS) {
			throw InvalidGameOperationException("Buy a whole number of units from 1 to ${OpportunityTerms.MAX_UNITS}.")
		}
		return units
	}

	/**
	 * [email] buys a military [hull] named [name] with [modules] (military ones up to the card's number) and a [crewClass]
	 * crew, appearing at once on the card's Planet (R113); refused during the galactic war (E3).
	 */
	fun buyMilitaryShip(email: String, slot: Int?, name: String?, hull: Hull?, modules: List<Module>, crewClass: CrewClass?): Game {
		val tipOff = opportunityToTake(email, slot)
		val terms =
			tipOff.tipOff.opportunity as? OpportunityTerms.MilitaryShip ?: throw InvalidGameOperationException("This opportunity isn't for a ship.")
		if (!news.shipsForSaleIn(runningTurn().number)) {
			throw InvalidGameOperationException("No ships can be bought during the galactic war.")
		}
		if (hull == null || hull !in terms.hulls) {
			throw InvalidGameOperationException("Choose ${terms.hulls.joinToString(" or ") { it.displayName }}.")
		}
		if (crewClass == null) {
			throw InvalidGameOperationException("Choose a crew class.")
		}
		if (modules.count { it.military } > terms.maxModules) {
			throw InvalidGameOperationException("At most ${terms.maxModules} military modules come with this hull.")
		}
		val ship = Ship.militaryOnPlanet(name, terms.at, hull, modules, crewClass)
		if (isShipNameTaken(ship.name)) {
			throw InvalidGameOperationException("There's already a ship called ${ship.name} in this game.")
		}
		return withOpportunityTaken(email, tipOff, playerOf(email).buyMilitaryShip(ship))
	}

	/**
	 * [email] fits [modules] from the card in [slot] to their ship [shipName] on its Planet (R113), taking [removed] off
	 * first when there's no room (creator, 2026-11-21; [Player.fitMilitaryModules]).
	 */
	fun buyMilitaryModules(email: String, slot: Int?, shipName: String?, modules: List<Module>, removed: List<Module> = emptyList()): Game {
		val tipOff = opportunityToTake(email, slot)
		val (allowed, at, max) = when (val terms = tipOff.tipOff.opportunity) {
			is OpportunityTerms.MilitaryModules -> Triple(terms.modules, terms.at, terms.modules.size)
			is OpportunityTerms.MilitaryShip -> Triple(Module.military(), terms.at, terms.maxModules).takeIf { terms.hullOptional }
			else -> null
		} ?: throw InvalidGameOperationException("This opportunity isn't for modules alone.")
		val oneEach = (tipOff.tipOff.opportunity as? OpportunityTerms.MilitaryModules) != null
		if (modules.isEmpty() || modules.any { it !in allowed } || modules.size > max || (oneEach && modules.distinct().size != modules.size)) {
			throw InvalidGameOperationException(
				"Choose up to $max of: ${allowed.joinToString(", ") {
					it.displayName
				}}${if (oneEach) ", each once" else ""}."
			)
		}
		val ship = playerOf(email).ship(shipName)
		if (ship?.isAt(at, ShipLocation.PLANET) != true) {
			throw InvalidGameOperationException("You need a ship on ${at.displayName}'s Planet.")
		}
		return withOpportunityTaken(email, tipOff, playerOf(email).fitMilitaryModules(shipName, modules, removed))
	}

	/** [email] accepts the delivery in [slot] with their ship [shipName] in the pickup Port (R114). */
	fun acceptDelivery(email: String, slot: Int?, shipName: String?): Game {
		val tipOff = opportunityToTake(email, slot)
		val terms =
			tipOff.tipOff.opportunity as? OpportunityTerms.Delivery ?: throw InvalidGameOperationException("This opportunity isn't a delivery.")
		val player = playerOf(email)
		if (player.ship(shipName)?.isInPortAt(terms.from) != true) {
			throw InvalidGameOperationException("You need a ship in ${terms.from.displayName}'s Port.")
		}
		return withOpportunityTaken(email, tipOff, player.takeDelivery(shipName, tipOff.tipOff, terms.space, terms.minReputation))
	}

	/**
	 * [email] sends their legal ship [shipName] on the expedition in [slot] (R114): a public d6 from [dice]; 1 loses it at
	 * once with any agents aboard, r brings it back in r turns.
	 */
	fun sendExpedition(email: String, slot: Int?, shipName: String?, dice: Dice): Game {
		val tipOff = opportunityToTake(email, slot)
		if (tipOff.tipOff.opportunity != OpportunityTerms.Expedition) {
			throw InvalidGameOperationException("This opportunity isn't the expedition.")
		}
		val number = runningTurn().number
		val roll = dice.d6()
		val player = playerOf(email)
		val name = player.ship(shipName)?.name ?: throw InvalidGameOperationException("Choose one of your ships.")
		val sent = player.sendOnExpedition(name, number + roll)
		val taken = withOpportunityTaken(email, tipOff, sent)
		if (roll == 1) {
			val lost = taken.playerOf(email).abandonExpedition(name)
			return taken.withPlayerChanged(lost, listOf("The $name expedition rolled 1 and was lost"), number)
		}
		return taken.withLogged(number, listOf("The $name expedition rolled $roll: back in turn ${number + roll}"))
	}

	/**
	 * [email]'s expedition ship [shipName], back this turn, returns on their Opportunity turn to the Port of [system] (its
	 * Space while Ports are closed), paid 2d6 × 50 HT from [dice], private (R114).
	 */
	fun returnExpedition(email: String, shipName: String?, system: StarSystem?, dice: Dice): Game {
		val current = opportunityAfterCollect(email)
		val expedition = playerOf(email).expeditionDue(shipName, current.number)
		return withExpeditionReturned(email, expedition, system ?: throw InvalidGameOperationException("Choose a system."), dice)
	}

	private fun withExpeditionReturned(email: String, expedition: Expedition, system: StarSystem, dice: Dice): Game {
		val number = runningTurn().number
		val location = if (news.conditions.portsClosedIn(number)) ShipLocation.SPACE else ShipLocation.PORT
		val back = playerOf(email).returnedFrom(expedition, system, location, dice.twoD6() * Expedition.PAY_PER_PIP)
		return withPlayerChanged(back, listOf("The ${expedition.ship.name} expedition returned to ${system.displayName}"), number)
	}

	/**
	 * [email] sells on the black market of [system]'s Planet every unit of illegal [goods] bound there aboard their ships
	 * on that Planet (R112): one d6 from [dice], +2 with Frank there, capped at 6, read on the creator's table; weapons at
	 * triple during the civil war (E5). Public without units or price.
	 */
	fun sellOnBlackMarket(email: String, system: StarSystem?, goods: SpecialGoods?, dice: Dice): Game {
		val current = opportunityAfterCollect(email)
		if (system == null || goods == null || !goods.illegal) {
			throw InvalidGameOperationException("Choose illegal goods and the system they're for.")
		}
		val player = playerOf(email)
		val roll = minOf(dice.d6() + player.blackMarketBonusAt(system), SpecialGoods.MAX_ROLL)
		val price =
			goods.blackMarketPrice(roll) * (if (goods == SpecialGoods.WEAPONS) news.conditions.weaponsMultiplierAt(system, current.number) else 1)
		val sold = player.sellSpecialAt(goods, system, ShipLocation.PLANET, price)
		val frank = player.blackMarketBonusAt(system).takeIf {
			it > 0
		}?.let { Agent.FRANK.actedLog(player.corporationName, ": +$it on the black market roll") }
		return withPlayerChanged(
			sold,
			listOfNotNull(frank, "${player.corporationName} sold on the black market at ${system.displayName}"),
			current.number
		)
	}

	/** [email] sells every fur aboard their ships in [system]'s Port that's bound there (R112, O5), at its fixed price. */
	fun sellFurs(email: String, system: StarSystem?): Game {
		val current = opportunityAfterCollect(email)
		val price = TipOff.entries.mapNotNull { it.opportunity as? OpportunityTerms.Furs }.firstOrNull { it.sellAt == system }?.salePrice
			?: throw InvalidGameOperationException("Furs don't sell there.")
		val sold = playerOf(email).sellSpecialAt(SpecialGoods.FURS, checkNotNull(system), ShipLocation.PORT, price)
		return withPlayerChanged(sold, listOf("${sold.corporationName} sold furs at ${system.displayName}"), current.number)
	}

	/**
	 * [email] sabotages [target] on their Opportunity turn (R97–R101), naming [blamedEmail] to take the blame on a 21+
	 * reputation roll (anyone but themselves and the target's owner; in a two-player game the target's owner). They pay 20
	 * HT × the target system's legal system up front. Silent in Port there foils it before any roll (R108). Otherwise both
	 * rolls are made with [dice], the effect applied (a ship's hits wait for its owner, a warehouse's missing units for the
	 * saboteur's choice) and the reputation changed, each logged; the effect line never names the saboteur (R101).
	 */
	fun sabotage(email: String, target: SabotageTarget, blamedEmail: String?, dice: Dice): Game {
		val current = opportunityAfterCollect(email)
		if (current.hasSabotaged(email)) {
			throw InvalidGameOperationException("You've already sabotaged this Opportunity.")
		}
		if (target.ownerEmail == email) {
			throw InvalidSabotageException("You can't sabotage your own assets.")
		}
		val owner = players.firstOrNull { it.email == target.ownerEmail } ?: throw InvalidSabotageException("Choose another player's asset.")
		owner.checkSabotageTarget(target)
		val blamed = blamedFor(email, owner.email, blamedEmail)
		val cost = Sabotage.COST_PER_LEGAL_LEVEL * target.system.locations.legalSystem
		val saboteur = playerOf(email)
		if (cost > saboteur.money) {
			throw InvalidSabotageException("Sabotage at ${target.system.displayName} costs $cost HT; you have ${saboteur.money} HT.")
		}
		val paid = saboteur.pay(cost, "Sabotage at ${target.system.displayName}")
		if (owner.isImmuneToSabotageAt(target.system)) {
			val foiled = Sabotage.foiled(email, target, blamed, cost, Agent.SILENT.displayName)
			val line = Agent.SILENT.actedLog(owner.corporationName, " foiled a sabotage at ${target.system.displayName}")
			return copy(players = replace(paid), turn = current.recordSabotage(foiled), log = log + LogEntry.event(current.number, line))
		}
		val shorty = saboteur.sabotageBonusAt(target.system)
		val criminal = saboteur.connections.criminal
		val factorySize = (target as? SabotageTarget.Factory)?.let { owner.factoryAt(it.system, it.goods)?.size } ?: 0
		val rolled = Sabotage.rolled(email, target, blamed, cost, criminal + shorty - factorySize, criminal + shorty, dice)
		val shortyLine =
			listOfNotNull(
				Agent.SHORTY.actedLog(saboteur.corporationName, ": +$shorty to a sabotage at ${target.system.displayName}").takeIf {
					shorty >
						0
				}
			)
		return copy(players = replace(paid), log = log + shortyLine.map { LogEntry.event(current.number, it) })
			.withSabotageEffect(rolled, current)
			.withSabotageReputation(rolled)
	}

	/** Who takes the blame for [saboteur]'s sabotage on [owner]'s asset (R97): [named], or the owner when nobody else is left. */
	private fun blamedFor(saboteur: String, owner: String, named: String?): String {
		if (players.size == MIN_PLAYERS_TO_START) {
			return owner
		}
		if (named == null || named == saboteur || named == owner || !hasPlayer(named)) {
			throw InvalidSabotageException("Choose an innocent player to take the blame: not yourself or the target.")
		}
		return named
	}

	/** This game once [sabotage]'s effect hit its target (R99), recorded on [current] and logged with a notice for the target's owner. */
	private fun withSabotageEffect(sabotage: Sabotage, current: Turn): Game {
		val target = sabotage.target
		val owner = playerOf(target.ownerEmail)
		val effect = sabotage.effect()
		val outcome = owner.sufferSabotage(target, effect)
		val notice = Notice(NoticeKind.SABOTAGE, target.system, listOf(owner.email), null, null)
		val lines = listOfNotNull(
			outcome.logged?.let { target.effectLog(corporationNames(), it) },
			(target as? SabotageTarget.Ship)?.name?.let { "$it is destroyed" }?.takeIf { outcome.destroyed }
		)
		return copy(
			players = replace(outcome.owner),
			turn = current.recordSabotage(outcome.lost?.let { sabotage.settledWith(it) } ?: sabotage),
			log = log + lines.map { LogEntry.notice(current.number, it, notice) }
		)
	}

	/** This game once [sabotage]'s reputation roll took its toll (R100): from the saboteur, or on 21+ from the innocent player named. */
	private fun withSabotageReputation(sabotage: Sabotage): Game {
		val number = runningTurn().number
		val change = if (sabotage.blamesInnocent()) {
			playerOf(sabotage.blamedEmail).loseReputation(SabotageTable.BLAME, "being blamed for sabotage")
		} else {
			playerOf(sabotage.saboteurEmail).loseReputation(sabotage.saboteurLoss(), "sabotage (${sabotage.reputationRollText()})")
		}
		return copy(players = replace(change.player), log = log + change.lines.map { LogEntry.event(number, it) })
	}

	/** [email] chooses the units missing from the warehouse their sabotage hit (R99): [lots], adding up to its effect. */
	fun chooseSabotageLosses(email: String, lots: Map<Goods, Int>): Game {
		playerOf(email)
		val current = runningTurn()
		current.requireOpportunity()
		val sabotage = current.lossChoiceOf(email) ?: throw InvalidGameOperationException("You have no warehouse losses to choose.")
		val owner = playerOf(sabotage.target.ownerEmail)
		val warehouse = checkNotNull(owner.warehouseAt(sabotage.target.system))
		lots.forEach { (goods, units) ->
			if (units > warehouse.holding(goods)) {
				throw InvalidSabotageException("The warehouse holds only ${warehouse.holding(goods)} ${goods.name}.")
			}
		}
		val chosen = sabotage.withLossChosen(lots, sabotage.effect())
		return copy(
			players = replace(owner.withWarehouseLoss(sabotage.target.system, chosen.lost)),
			turn = current.withSabotageChanged(sabotage, chosen)
		)
	}

	/**
	 * [email] places a sabotage's hits on their ship [shipName] as [placement] (R99): as in battle (R76), out of turn, the
	 * POLY and ISO that no longer fits going to their warehouse there as far as it fits. Once nothing is owed, Opportunity
	 * may close (R102).
	 */
	fun placeSabotageHits(email: String, shipName: String, placement: HitPlacement): Game {
		val owner = playerOf(email)
		val current = runningTurn()
		current.requireOpportunity()
		val sabotage = current.sabotageHitsOn(email, shipName)
		val (hit, lost) = owner.takeSabotageHits(shipName, sabotage.effect(), placement)
		val destroyed = listOfNotNull("$shipName is destroyed".takeIf { hit.ship(shipName) == null })
		return copy(
			players = replace(hit),
			turn = current.withSabotageChanged(sabotage, sabotage.settledWith(lost)),
			log = log + destroyed.map { LogEntry.event(current.number, it) }
		).withOpportunityClosedIfOver()
	}

	/**
	 * [email] recruits on their Opportunity turn (R103), once: paying [payment] HT, then 2d6 × 5 from [dice] at or below it
	 * brings a random agent from the pool to [start]. Only a success is logged, without the agent (R105).
	 */
	fun recruitAgent(email: String, payment: Int?, start: AgentPosition, dice: Dice): Game {
		val current = opportunityAfterCollect(email)
		val pool = agentPool()
		if (pool.isEmpty()) {
			throw InvalidGameOperationException("No agents are left to recruit.")
		}
		val recruitedTurn = current.recruitedByPlayer(email)
		val player = playerOf(email)
		val roll = dice.twoD6()
		val picked = pool[dice.oneOf(pool.size) - 1]
		val recruited = player.recruitAgent(payment, roll, picked, start, current.number)
		val joined = recruited.agents.size > player.agents.size
		// Playtest 2026-11-21: where the agent starts is chosen after joining, so the line no longer names a system.
		val line = listOfNotNull("${player.corporationName} recruited an agent".takeIf { joined })
		return copy(players = replace(recruited), turn = recruitedTurn, log = log + line.map { LogEntry.event(current.number, it) })
	}

	/** [email] places their [agent], recruited this turn, where it starts (playtest, 2026-11-21): see [Player.placeNewAgent]. */
	fun placeNewAgent(email: String, agent: Agent?, to: AgentPosition): Game {
		val current = runningTurn()
		return copy(players = replace(playerOf(email).placeNewAgent(agent, to, current.number)))
	}

	/** [email] moves their [agent] [to] at any time in a running game, like cargo (R104): see [Player.moveAgent]. The public counts follow. */
	fun moveAgent(email: String, agent: Agent?, to: AgentPosition): Game {
		runningTurn()
		return copy(players = replace(playerOf(email).moveAgent(agent, to)))
	}

	/** [email] offers their [agent] to [buyerEmail] for [price] HT on their Opportunity turn (R106). Private until it's bought. */
	fun offerAgent(email: String, agent: Agent?, buyerEmail: String?, price: Int?): Game {
		val current = opportunityAfterCollect(email)
		val offered = playerOf(email).checkAgentForSale(agent)
		if (buyerEmail == null || buyerEmail == email || !hasPlayer(buyerEmail)) {
			throw InvalidAgentException("Choose another player to sell to.")
		}
		if (price == null || price < 0) {
			throw InvalidAgentException("Enter a whole number of HT, 0 or more.")
		}
		return copy(turn = current.withAgentOffered(AgentOffer(0, email, buyerEmail, offered, price)))
	}

	/**
	 * [email] answers the agent offer [id] made to them (R106), any time before Opportunity closes: [accept]ing pays the seller
	 * and the agent joins them, on its own where it stood, its identity theirs now. The sale is public, the price isn't.
	 */
	fun answerAgentOffer(email: String, id: Int?, accept: Boolean): Game {
		val buyer = playerOf(email)
		val current = runningTurn()
		val offer = current.agentOffer(id)
		offer.requireBuyer(email)
		val after = current.withoutAgentOffer(offer)
		if (!accept) {
			return copy(turn = after)
		}
		val seller = playerOf(offer.sellerEmail)
		val place = seller.placeOfAgent(offer.agent)?.takeIf { it.location != ShipLocation.SPACE }
			?: throw InvalidGameOperationException("This agent can't be sold now.")
		val bought = buyer.buyAgent(offer.agent, offer.price, place)
		val sold = seller.sellAgent(offer.agent, offer.price)
		val line = "${seller.corporationName} sold an agent at ${place.system.displayName} to ${buyer.corporationName}"
		return copy(
			players = players.map {
				when (it.email) {
					email -> bought
					seller.email -> sold
					else -> it
				}
			},
			turn = after,
			log = log + LogEntry.event(current.number, line)
		)
	}

	/** This game moved on to Investment if its Opportunity is over (R102), else unchanged. */
	private fun withOpportunityClosedIfOver(): Game {
		val current = runningTurn()
		return if (current.isOpportunityOver()) openInvestment(current) else this
	}

	/**
	 * [email] buys [blocks] warehouse blocks at [system] (R45, E06-S04): [Player.buyWarehouseBlocks] checks and applies it,
	 * and the public log says who added how many where, without the price (R50). Only in Investment and, for a player with
	 * factories, after they collected (S03-5), so the new blocks never catch this turn's output (R48). Done is a readiness
	 * flag (D09): a player who signalled done may still invest until everyone has.
	 */
	fun buyWarehouseBlocks(email: String, system: StarSystem, blocks: Int): Game =
		withInvestment(email, { it.warehouseBlocksLog(system, blocks) }) { it.buyWarehouseBlocks(system, blocks) }

	/**
	 * [email] buys [units] of production of [goods] at [system] (R46, E06-S05): [Player.buyFactoryUnits] checks and applies
	 * it at the game's current price for that good there, and the public log says who added how many of what where, without
	 * the price (R50). Only in Investment and, for a player with factories, after they collected (S03-5); the new units first
	 * produce at the next turn's Collect (R48). A player with no factory when Investment opened had nothing to collect, so
	 * their first factory doesn't make them collect from it, which would block them and have it produce this turn: they are
	 * recorded as collected. Done is a readiness flag (D09).
	 */
	fun buyFactoryUnits(email: String, system: StarSystem, goods: Goods, units: Int): Game =
		withInvestment(email, { it.factoryUnitsLog(system, goods, units) }) { it.buyFactoryUnits(system, goods, units, prices) }

	/**
	 * [email] sells [units] units of their [goods] factory at [system] (R96): [Player.sellFactoryUnits] checks and applies it
	 * at 5 × the good's price there. Public without the amount (R29).
	 */
	fun sellFactoryUnits(email: String, system: StarSystem, goods: Goods, units: Int): Game {
		val factory = playerOf(email).factoryAt(system, goods)
		return withInvestment(email, { player -> checkNotNull(factory).soldLog(player.corporationName, units) }) {
			it.sellFactoryUnits(system, goods, units, prices)
		}
	}

	/** [email] borrows [amount] HT over [term] in Investment (R90, R91): see [Player.takeLoan]. Private, not logged (R29). */
	fun takeLoan(email: String, amount: Int?, term: LoanTerm?): Game {
		val number = investing().number
		return withInvestment(email, null) { it.takeLoan(amount, term, number) }
	}

	/** [email] pays this turn's loan interest (R92): once per Investment, while it's due. Private, not logged (R29). */
	fun payInterest(email: String): Game {
		val player = playerOf(email)
		val current = investing()
		val paid = current.interestPaidBy(email)
		return copy(players = replace(player.payInterest(current.number)), turn = paid)
	}

	/** [email] repays their whole loan (R93), with this turn's interest when it's due and unpaid. Private, not logged (R29). */
	fun repayLoan(email: String): Game {
		val player = playerOf(email)
		val current = investing()
		val unpaid = player.owesInterestIn(current.number) && !current.hasPaidInterest(email)
		return copy(players = replace(player.repayLoan(unpaid)))
	}

	/**
	 * [email] buys a ship (R61, E09-S02): [Ship.commission] checks what it's made of, the game that its name isn't taken by
	 * any ship in it, ignoring case (R62), and [Player.buyShip] that they can pay. It waits in the yard (R63). The public
	 * log names the ship and the yard, never the hull or the price (R50, R57). Only in Investment, after their Collect,
	 * like any purchase (R48).
	 */
	fun buyShip(email: String, name: String?, shipyard: StarSystem, hull: Hull, modules: List<Module>, crewClass: CrewClass): Game {
		if (!news.shipsForSaleIn(runningTurn().number)) {
			throw InvalidInvestmentException("No ships can be bought during the galactic war.")
		}
		val ship = Ship.commission(name, shipyard, hull, modules, crewClass, runningTurn().number)
		if (isShipNameTaken(ship.name)) {
			throw InvalidInvestmentException("There's already a ship called ${ship.name} in this game.")
		}
		return withInvestment(email, { it.shipOrderedLog(ship.name, shipyard) }) { it.buyShip(ship) }
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
	fun sellShip(email: String, shipName: String): Game = withInvestment(email, { it.shipSoldLog(shipName) }) { it.sellShip(shipName) }

	/**
	 * This game once [change] applied to [email] in Investment, with the line [logged] writes from the player in the public
	 * log when there is one.
	 */
	private fun withInvestment(email: String, logged: ((Player) -> String)?, change: (Player) -> Player): Game {
		val player = playerOf(email)
		val investing = investing()
		val line = logged?.let { LogEntry.event(investing.number, it(player)) }
		return copy(players = replace(change(player)), log = log + listOfNotNull(line))
	}

	/**
	 * What [email] may see of this game beyond its public state ([PlayerView], R17, R29, A05), or null when they aren't a
	 * member. Each part decides whether its data is theirs: the counter's awaited trade, the wholesale ask, the interception
	 * question, the battle and its report; this only looks the parts up for the viewer.
	 */
	fun viewFor(email: String): PlayerView? {
		val player = players.firstOrNull { it.email == email } ?: return null
		val resolution = turn?.resolution
		val lossChoice = turn?.lossChoiceOf(email)
		return PlayerView(
			player = player,
			offers = turn?.offersOf(email).orEmpty(),
			untradedOffers = turn?.let { it.untradedOffersOf(email, tradesIn(it.number)) }.orEmpty(),
			bid = turn?.bidOf(email),
			tradeMax = resolution?.awaitingTradeOf(email)?.let { player.tradeMax(it.offer, it.marketMax) },
			wholesaleAsk = resolution?.takeIf { it.isAskingWholesaler(email) },
			factoriesToCollect = player.factories.takeIf { turn?.isCollecting(email) == true && player.hasFactories() },
			investment = InvestmentView(
				freeShipNames(),
				turn?.hasRaisedConnection(email) == true,
				turn?.hasBoughtReputation(email) == true
			).takeIf { canInvest(email) },
			interception = turn?.interception?.takeIf { it.asks(email) },
			battle = turn?.battle?.takeIf { it.involves(email) },
			battleReport = turn?.battleReport?.reportFor(email),
			prices = prices,
			loan = player.loan?.let { LoanView(it, turn?.number ?: 0, turn?.hasPaidInterest(email) == true, player.borrowingCap()) }
				?: turn?.takeIf { it.phase == TurnPhase.INVESTMENT }?.let { LoanView(null, it.number, false, player.borrowingCap()) },
			sabotagesOrdered = turn?.sabotagesBy(email).orEmpty(),
			sabotagesSuffered = turn?.sabotagesOn(email).orEmpty(),
			sabotagedWarehouse = lossChoice?.let { playerOf(it.target.ownerEmail).warehouseAt(it.target.system) },
			agentOffers = turn?.agentOffersConcerning(email).orEmpty().map { offer ->
				AgentOfferView(offer, playerOf(offer.sellerEmail).placeOfAgent(offer.agent), offer.agent.takeIf { offer.sellerEmail == email })
			},
			limpyAsk = turn?.takeIf { it.limpyAsk == email }?.tradingQueue?.firstOrNull(),
			agentPool = agentPool().size,
			opportunity = turn?.takeIf { it.phase == TurnPhase.OPPORTUNITY }?.let {
				OpportunityView(it.hasSabotaged(email), it.hasRecruited(email), it.hasCollected(email))
			},
			tipOffs = news.inPlay.map { TipOffView(it, email, player.connections) },
			landingOdds = player.ships.associate { it.name to LandingOdds(player.landingBonusFor(it.name), planetSecurity(it.system)) },
			jumpChances = player.ships.associate { it.name to it.jumpChances(jumpBonusOf(player, it)) },
			turnNumber = turn?.number ?: 0
		)
	}

	/** The trades of turn [number], from the log, where each is kept (creator, 2026-11-18). */
	private fun tradesIn(number: Int): List<TradeRecord> = log.filter { it.turn == number }.mapNotNull { it.trade }

	/** The agents still to recruit (R103): every recruitable one nobody owns and nobody killed. */
	fun agentPool(): List<Agent> = Agent.entries.filter { agent ->
		agent.recruitable && agent !in deadAgents && players.none { it.agent(agent) != null }
	}

	/**
	 * Whether [email] may buy in this Investment now (R45): the game is running in Investment. Collect is in Opportunity
	 * now (R102), so it never waits for it. False for anyone who isn't in the game.
	 */
	fun canInvest(email: String): Boolean {
		val current = turn ?: return false
		return hasPlayer(email) && status == GameStatus.RUNNING && current.phase == TurnPhase.INVESTMENT
	}

	/** The running Investment (R44). */
	private fun investing(): Turn {
		val investing = runningTurn()
		investing.requireInvesting()
		return investing
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
	 * Records that [email] is done bidding (R17), once they have placed their starting connection points (R82,
	 * E12-S01-3). Bidding closes the moment the last player signals done (D09), rolling
	 * the initiative order with [dice]; a signal after that is refused (A04).
	 */
	fun signalDoneBidding(email: String, dice: Dice): Game {
		playerOf(email).requireConnectionsPlaced()
		val signalled = copy(turn = runningTurn().signalDoneBidding(email))
		return if (signalled.runningTurn().everyoneDoneBidding(players.map { it.email })) signalled.advancePhase(dice) else signalled
	}

	/**
	 * [email] places their starting connection points (R82, E12-S01-2): [Player.placeConnections] checks and applies them,
	 * once, and the placement is public at once. Only in Bidding, which is where turn 1 starts; no player has points left
	 * after it, so the player refuses it in any later turn.
	 */
	fun placeConnections(email: String, political: Int?, business: Int?, criminal: Int?): Game {
		val player = playerOf(email)
		val current = runningTurn()
		current.requireBidding()
		val placed = player.placeConnections(political, business, criminal)
		return copy(players = replace(placed), log = log + LogEntry.event(current.number, placed.connectionsPlacedLog()))
	}

	/**
	 * [email] raises their [kind] connections by one point (R83, E12-S02): once per Investment ([Turn.connectionRaisedBy]),
	 * after their Collect like any purchase (S03-5), with [Player.raiseConnection] checking the level and the money. The
	 * raise is public, its cost isn't (R29).
	 */
	fun raiseConnection(email: String, kind: ConnectionKind): Game {
		val player = playerOf(email)
		val investing = investing()
		val raisedTurn = investing.connectionRaisedBy(email)
		val raised = player.raiseConnection(kind)
		val change = player.connectionReputationChange(kind)
		val lines = listOf(raised.connectionRaisedLog(kind)) +
			raised.reputationLog(change, "raising ${kind.displayName} connections", change - kind.reputationChange)
		return copy(players = replace(raised), turn = raisedTurn, log = log + lines.map { LogEntry.event(investing.number, it) })
	}

	/**
	 * [email] buys reputation (R87, E13-S03): once per Investment ([Turn.reputationBoughtBy]), after their Collect like any
	 * purchase (S03-5), for 20 HT and a d6 from [dice] ([Player.buyReputation]). Logged with the roll, not the cost (R29).
	 */
	fun buyReputation(email: String, dice: Dice): Game {
		val player = playerOf(email)
		val investing = investing()
		val boughtTurn = investing.reputationBoughtBy(email)
		val roll = dice.d6()
		val bought = player.buyReputation(roll)
		return copy(
			players = replace(bought),
			turn = boughtTurn,
			log = log + LogEntry.event(investing.number, bought.reputationLog(roll, "buying reputation (rolled $roll)"))
		)
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
	fun pickTurnOrderPlace(email: String, place: Int?, dice: Dice): Game {
		val current = turnAwaiting(email, Turn::pickerToDecide, Turn::pickerWaitingText)
		val settled = current.pickPlace(email, place)
		val events = settled.turnOrderLog(current.turnOrderPicks.size, corporationNames())
		val picked = copy(turn = settled, log = log + events.map { LogEntry.event(current.number, it) })
		return if (settled.picker() == null) picked.openNews(dice) else picked
	}

	/**
	 * Initiative → News (R109), once the turn order is set: due tip-offs become effective and new ones are drawn with
	 * [dice]. A News with no tip-off in play has nothing to read, so it closes at once into Jumps.
	 */
	private fun openNews(dice: Dice): Game {
		val current = runningTurn().toNews()
		val due = news.dueIn(current.number)
		val effective = due.fold(copy(turn = current, news = news.afterEffectiveIn(current.number))) { game, tipOff ->
			val taker = tipOff.takenBy?.let { corporationOf(it) }
			val notice = tipOff.announcementNotice()
			val announced = game.withNoticed(current.number, tipOff.announcement(taker), notice)
			// Playtest 2026-11-21: what the event did (ships lost, reputation, tax) goes in its pop-up too.
			tipOff.tipOff.event?.let { announced.withEventTakenPlace(it, dice).withNoticeFrom(announced.log.size, notice) } ?: announced
		}
		val opened = effective.withTipOffsDrawn(current.number, dice)
		return if (opened.news.hasTipOffsInPlay()) opened else opened.openJumps(opened.runningTurn())
	}

	/** This game with turn [number]'s tip-offs drawn (R109) and the draw logged: the roll and each new one's need and turn. */
	private fun withTipOffsDrawn(number: Int, dice: Dice): Game {
		val draw = news.drawIn(number, dice)
		val line = draw.roll?.let { roll ->
			val drawn = draw.drawn.joinToString(", ") { "#${it.slot} ${it.tipOff.need.text()}, effective turn ${it.effectiveIn()}" }
			"News: rolled $roll for new tip-offs" + if (drawn.isEmpty()) "" else ": $drawn"
		}
		return copy(news = draw.news).withLogged(number, listOfNotNull(line))
	}

	/** This game with [changed] as its turn. */
	private fun withTurn(changed: Turn): Game = copy(turn = changed)

	/** This game with [lines] logged as public events of turn [number]. */
	private fun withLogged(number: Int, lines: List<String>): Game = copy(log = log + lines.map { LogEntry.event(number, it) })

	/**
	 * What [ship] of [player]'s gets on a jump beyond its own crew and modules (R68): Novak aboard (R108) and this turn's
	 * news (E1). Both the roll and the chance its player sees use it (playtest, 2026-11-21: the chance ignored the news).
	 */
	private fun jumpBonusOf(player: Player, ship: Ship): Int {
		val newsModifier = news.conditions.jumpModifierIn(turn?.number ?: 0)
		return player.jumpBonusFor(ship.name) + newsModifier
	}

	/** This game with the log's lines from index [from] on that have no pop-up moment given [notice]. */
	private fun withNoticeFrom(from: Int, notice: Notice): Game = copy(log = log.take(from) + log.drop(from).map { it.noticed(notice) })

	/** This game with [line] logged as a pop-up moment, [notice] (playtest, 2026-11-21: News's announcements). */
	private fun withNoticed(number: Int, line: String, notice: Notice): Game = copy(log = log + LogEntry.notice(number, line, notice))

	/**
	 * [event] takes place in this turn's News (R115), with [dice] for its rolls: price moves are one-off (R35's 1–20),
	 * S/D shifts permanent, and "this turn" changes last until the next News ([news]'s conditions).
	 */
	private fun withEventTakenPlace(event: NewsEvent, dice: Dice): Game {
		val number = runningTurn().number
		val conditions = news.conditions
		return when (event) {
			NewsEvent.PSIONIC_FIELD -> withConditions(conditions.withJumpModifier(PSIONIC_JUMP_MODIFIER))
			NewsEvent.ALIEN_ATTACK -> withPricesShifted(-3) { true }
			NewsEvent.GALACTIC_WAR ->
				withPricesShifted(3) { it.goods == Goods.CHIPS }.withPricesShifted(6) { it.goods == Goods.POLY }.withPricesShifted(2) {
					it.goods ==
						Goods.ISO
				}
					.withConditions(conditions.withPoliceChange(4)).withNoShipsThrough(number + 1)
			NewsEvent.MONEY_FRAUD -> copy(players = players.map { it.inflated() })
			NewsEvent.CIVIL_WAR -> withCivilWarAt(StarSystem.GAMMA_LEPORIS, number)
			NewsEvent.NEW_COLONY -> withPricesShifted(5) { it.system == StarSystem.MU_HERCULIS }
			NewsEvent.CHIPS_BREAKTHROUGH -> copy(supplyDemandShifts = supplyDemandShifts.shifted(Goods.CHIPS, 3))
			NewsEvent.SYNTHETIC_FOOD -> copy(supplyDemandShifts = supplyDemandShifts.shifted(Goods.FOOD, 2))
			NewsEvent.ISO_BREAKTHROUGH -> copy(supplyDemandShifts = supplyDemandShifts.shifted(Goods.ISO, -3))
			NewsEvent.EPIDEMICS ->
				withPricesShifted(4) { true }.withConditions(conditions.withPortsClosed().withSecurityChange(1).withPoliceChange(1))
			NewsEvent.GOVERNMENT_SCREENING -> players.fold(this) { game, player ->
				val loss = player.loseReputation(dice.twoD6(), "government screening (rolled 2d6)")
				game.withPlayerChanged(loss.player, loss.lines, number)
			}
			NewsEvent.PIRATES -> withPiratesAt(
				StarSystem.MU_HERCULIS,
				number,
				dice
			).withConditions(conditions.withSecurityChange(2).withPoliceChange(2))
			NewsEvent.SPECIAL_TAX -> {
				val paying = players.filter { it.money >= it.specialTax() }.map { it.corporationName }
				copy(players = players.map { it.afterSpecialTax() })
					.withLogged(number, listOf("Special tax paid by: ${paying.joinToString(", ").ifEmpty { "nobody" }}"))
			}
			NewsEvent.NEW_POLYMER_SYSTEM ->
				withPricesShifted(-5) { it.goods == Goods.POLY }.withPricesShifted(2) { it.goods == Goods.ISO || it.goods == Goods.CHIPS }
		}
	}

	private fun withPricesShifted(delta: Int, applies: (MarketGood) -> Boolean): Game = copy(prices = prices.shifted(delta, applies))

	private fun withConditions(changed: TurnConditions): Game = copy(news = news.withConditions(changed))

	/** This game with no ship to be bought through turn [last] (E3). */
	private fun withNoShipsThrough(last: Int): Game = copy(news = news.withNoShipsThrough(last))

	/**
	 * The civil war at [system] (E5): black-market weapons sell for triple there this turn, and every player's ships in its
	 * Port, factories and warehouse there are confiscated for half their value, each owner logged without amounts.
	 */
	private fun withCivilWarAt(system: StarSystem, number: Int): Game {
		val hit = players.filter { it.hasAssetsAt(system) }.map { it.corporationName }
		return copy(players = players.map { if (it.hasAssetsAt(system)) it.afterCivilWar(system, prices) else it })
			.withConditions(news.conditions.withTripledWeaponsAt(system))
			.withLogged(
				number,
				listOfNotNull(
					hit.takeIf {
						it.isNotEmpty()
					}?.let { "Confiscated at ${system.displayName}: ${it.joinToString(", ")}" }
				)
			)
	}

	/**
	 * Pirates at [system] (E12): every warehouse there is emptied, and each ship in its Port rolls a d6 with [dice]: 4–6 it
	 * escapes into Space, 1–3 it's lost with its cargo (agents step off into the Port, R107). Each ship's fate is logged.
	 */
	private fun withPiratesAt(system: StarSystem, number: Int, dice: Dice): Game = players.fold(this) { game, owner ->
		val looted = game.playerOf(owner.email).lootWarehouse(system)
		looted.player.shipsInPortAt(system).fold(game.withPlayerChanged(looted.player, looted.lines, number)) { current, ship ->
			val roll = dice.d6()
			val player = current.playerOf(owner.email)
			val (changed, line) = if (roll > PIRATE_ESCAPE_ABOVE) {
				player.landShip(ship.name, ShipLocation.SPACE) to
					"${ship.name} (${player.corporationName}) escaped the pirates into Space (rolled $roll)"
			} else {
				player.confiscateShip(ship.name) to "${ship.name} (${player.corporationName}) was lost to the pirates (rolled $roll)"
			}
			current.withPlayerChanged(changed, listOf(line), number)
		}
	}

	/**
	 * [email] learns the tip-off in board [slot] (R110): only in News, for 5 HT, with their connection of its kind at its
	 * level now. Private: not logged.
	 */
	fun learnTipOff(email: String, slot: Int?): Game {
		val player = playerOf(email)
		runningTurn().requireNews()
		val tipOff = news.inSlot(slot)
		if (tipOff.isLearnedBy(email)) {
			throw InvalidGameOperationException("You already know this tip-off.")
		}
		return copy(players = replace(player.learnTipOff(tipOff.tipOff.need)), news = news.withTipOff(tipOff.learnedBy(email)))
	}

	/** News → Jumps (R44): the first jump slot starts, skipping players with nothing able to jump (R64). */
	private fun openJumps(current: Turn): Game = copy(turn = current.toJumps()).withEmptyJumpSlotsSkipped()

	/** [email] is done with the news (R109); once everyone is, Jumps starts. */
	fun signalDoneNews(email: String): Game {
		playerOf(email)
		val signalled = runningTurn().signalDoneNews(email)
		val game = copy(turn = signalled)
		return if (signalled.everyoneDoneWithNews(players.map { it.email })) game.openJumps(signalled) else game
	}

	/**
	 * [email] jumps their ship [shipName] to [destination] during their slot (R26, S02); see [Ship.jumpTo]. Each ship
	 * jumps at most once per turn, and nothing else happens while a ship waits to land (S03). [dice] rolls 2d6 against the
	 * ship's [JumpChance] (R68): a failed jump leaves the ship where it was, with its jump used, and ends the slot when it
	 * was the last ship to jump, as a landing does. Either way the jump and its roll are logged (S02-7).
	 */
	fun jumpShip(email: String, shipName: String?, destination: StarSystem?, dice: Dice): Game {
		val current = turnAwaiting(email, Turn::currentJumper, Turn::jumperWaitingText)
		current.refuseDuringFight()
		current.refuseWhileAShipWaitsToLand()
		val player = playerOf(email)
		val ship = player.ship(shipName)?.takeUnless { current.hasJumped(it) }
			?: throw InvalidJumpException("Choose one of your ships that hasn't jumped this turn.")
		if (destination == null) {
			throw InvalidJumpException("Choose a destination system.")
		}
		if (ship.isInPort() && news.conditions.portsClosedIn(current.number)) {
			throw InvalidJumpException("Ports are closed this turn (epidemics): ${ship.name} can't leave the Port.")
		}
		if (destination == ship.system) {
			return withShipMovedWithin(player, ship, current)
		}
		val roll = dice.twoD6()
		val novak = player.jumpBonusFor(ship.name)
		val novakLog = listOfNotNull(
			Agent.NOVAK.actedLog(player.corporationName, ": +$novak to ${ship.name}'s jump").takeIf { novak > 0 }
		).map { LogEntry.event(current.number, it) }
		if (!ship.checkedJumpChanceTo(destination, jumpBonusOf(player, ship)).succeeds(roll)) {
			val recorded = current.recordFailedJump(ship)
			val failed = copy(
				turn = recorded,
				log = log + novakLog + LogEntry.event(current.number, player.failedJumpLog(ship.name, destination, roll))
			)
			return if (recorded.haveAllJumped(player.jumpableShips())) failed.endJumpSlot(email) else failed
		}
		val moved = player.jumpShip(ship, destination)
		return copy(
			players = replace(moved),
			turn = current.recordJump(ship),
			log = log + novakLog + LogEntry.event(current.number, moved.jumpedLog(ship.name, roll))
		).withInterceptionAsked(ship.name, email, destination)
	}

	/** [ship] of [player]'s moved into its own system's Space (R69): no route, so no roll; it can be intercepted (R70). */
	private fun withShipMovedWithin(player: Player, ship: Ship, current: Turn): Game {
		val moved = player.jumpShip(ship, ship.system)
		return copy(
			players = replace(moved),
			turn = current.recordJump(ship),
			log = log + LogEntry.event(current.number, moved.jumpedLog(ship.name, null))
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
		val interception = Interception(system, shipName, ownerEmail, asked, emptyList())
		return copy(
			turn = current.withInterception(interception),
			log = log + LogEntry.event(current.number, interception.askedText(corporationNames()))
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
		val events = mutableListOf<LogEntry>()
		val caught = mutableListOf<BattleInterception>()
		var penalized = players
		val arrivingBonus = playerOf(interception.ownerEmail).interceptBonusFor(arriving.name)
		for ((email, name) in tries) {
			val interceptor = checkNotNull(playerOf(email).ship(name))
			val ours = dice.d6()
			val theirs = dice.d6()
			val ourBonus = playerOf(email).interceptBonusFor(name)
			val success = interceptor.interceptTotal(ours) + ourBonus > arriving.interceptTotal(theirs) + arrivingBonus
			if (success) {
				caught += BattleInterception(arriving.name, interceptor.name)
			}
			val roll = InterceptionRoll(
				email,
				interceptor.name,
				interceptor.interceptLog(ours, ourBonus),
				interception.ownerEmail,
				arriving.name,
				arriving.interceptLog(theirs, arrivingBonus),
				success
			)
			val notice = interception.tryNotice(roll)
			val triggers = listOfNotNull(
				triggerLog(email, interceptor.name, ourBonus),
				triggerLog(interception.ownerEmail, arriving.name, arrivingBonus)
			)
			events += triggers.map { LogEntry.notice(current.number, it, notice) }
			events += LogEntry.notice(current.number, roll.logText(), notice)
			// R86 (E13-S04): every try is a hostile act and costs 2d6 of reputation, unless the trying ship has a working Battle comm.
			if (!interceptor.hasWorkingBattleComm()) {
				val loss = dice.twoD6()
				val lost = penalized.first { it.email == email }.loseReputation(loss, "trying to intercept (rolled 2d6: $loss)")
				penalized = penalized.map { if (it.email == email) lost.player else it }
				events += lost.lines.map { LogEntry.notice(current.number, it, notice) }
			}
		}
		if (tries.isEmpty()) {
			events += LogEntry.event(current.number, interception.nobodyTriedText())
		}
		val attempts = tries.map { (_, name) -> BattleInterception(arriving.name, name) }
		val decided = copy(
			players = penalized,
			turn = current.withInterception(null).recordInterceptionTries(attempts),
			log = log + events
		)
		return if (caught.isEmpty()) decided else decided.withBattleStarted(interception.system, caught)
	}

	/** Trigger's line when he added [bonus] to [email]'s [shipName]'s interception roll (R105, R108), or null. */
	private fun triggerLog(email: String, shipName: String, bonus: Int): String? =
		Agent.TRIGGER.actedLog(corporationOf(email), ": +$bonus to $shipName's intercept").takeIf { bonus > 0 }

	/**
	 * This game with a battle at [system] among every ship in Space there (R73, R80), [interceptions] holding their ships;
	 * the turn's interception tries, successful or not, decide whom firing costs reputation (R86).
	 */
	private fun withBattleStarted(system: StarSystem, interceptions: List<BattleInterception>): Game {
		val current = runningTurn()
		val fighting = players.flatMap { player -> player.shipsInSpaceAt(system).map { BattleShip(it.name, player.email) } }
		val battle = Battle.start(system, fighting, interceptions, current.interceptionTries)
		val started = LogEntry.notice(current.number, battle.startedText(), battle.notice(NoticeKind.BATTLE_STARTED))
		return copy(turn = current.withBattleStarted(battle), log = log + started)
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
		val xLog = mutableListOf<String>()
		val salvos = battle.fireOrders().map { order ->
			val target = order.targetShip()
			val owner = players.first { it.ship(order.ship) != null }
			val bonus = if (order.rockets) 0 else owner.weaponsBonusFor(order.ship)
			if (bonus > 0 && !battle.isBreakingAway(target)) {
				xLog += Agent.X.actedLog(owner.corporationName, ": +$bonus weapons for ${order.ship}")
			}
			Salvo.fire(shipNamed(order.ship), shipNamed(target), order.rockets, battle.isBreakingAway(target), bonus, dice)
		}
		val round = battle.fired(salvos, battle.fightingShips().associateWith { shipNamed(it).hitsSurvivable() })
		// R86 (E13-S04): firing at a ship that didn't try to intercept you costs 2d6 of reputation, once per battle.
		val penalties = battle.firingPenalties().associateWith { dice.twoD6() }
		val losses = players.associate { player ->
			player.email to penalties[player.email]?.let {
				player.loseReputation(it, "firing at a ship that didn't try to intercept (rolled 2d6: $it)")
			}
		}
		val penalized = players.map { losses[it.email]?.player ?: it }
		val penaltyLog = penalties.keys.flatMap { losses.getValue(it)?.lines.orEmpty() }
		val killedLog = round.destroyed.mapNotNull { ship ->
			players.firstNotNullOfOrNull { it.takeIf { owner -> owner.ship(ship) != null } }?.agentsKilledLog(ship)
		}
		val killed = round.destroyed.flatMap { ship -> players.flatMap { owner -> owner.agents.filter { it.isAboard(ship) }.map { it.agent } } }
		return copy(
			players = penalized.map { player -> round.destroyed.fold(player) { owner, ship -> owner.loseShip(ship) } },
			turn = current.withBattleRound(round.battle.firingPenalizedFor(penalties.keys.toList()), round.report),
			log = log + (xLog + round.log + penaltyLog + killedLog).map {
				LogEntry.notice(current.number, it, battle.roundNotice())
			},
			deadAgents = deadAgents + killed
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
		val killed = player.agents.filter { agent -> destroyed.any { agent.isAboard(it) } }.map { it.agent }
		return copy(
			players = replace(hit),
			turn = current.withHitsPlaced(battle.hitsPlaced(email, destroyed), placements.keys, destroyed),
			log = log + destroyed.flatMap {
				listOf("$it is destroyed") + listOfNotNull(player.agentsKilledLog(it))
			}.map { LogEntry.notice(current.number, it, battle.notice(NoticeKind.SHIP_DESTROYED)) },
			deadAgents = deadAgents + killed
		).withBattleMovedOn()
	}

	/**
	 * [email] places each of their surviving ships from the battle at Port, Planet or Space (R77), [locations] by ship
	 * name; each is logged as a landing. Once everyone has, the battle is over and the jumps go on.
	 */
	fun landAfterBattle(email: String, locations: Map<String, ShipLocation>, dice: Dice): Game {
		playerOf(email)
		val current = runningTurn()
		val battle = current.battleNow()
		val landed = battle.landedBy(email, locations.keys)
		locations.values.forEach { if (!it.isLandable()) throw InvalidLandingException() }
		val survivors = battle.survivorsOf(email)
		val placed = survivors.fold(this) { game, ship -> game.withShipPlaced(email, ship, locations.getValue(ship), dice) }
		return placed.withTurn(current.withBattle(landed)).withBattleMovedOn()
	}

	/**
	 * This game once its battle moved on: when it's over, that is logged; once every survivor has been placed, the
	 * battle is gone, the ship that arrived has landed or been destroyed, and the jump slot goes on or ends (S02-6).
	 */
	private fun withBattleMovedOn(): Game {
		val current = runningTurn()
		val battle = checkNotNull(current.battle)
		val over = battle.notice(NoticeKind.BATTLE_OVER)
		val events = listOfNotNull(battle.overText().takeIf { battle.isJustOver() }).map { LogEntry.notice(current.number, it, over) }
		if (!battle.isFinished()) {
			return copy(log = log + events)
		}
		val after = current.withBattle(null).landed()
		val told = log + events
		val summary = LogEntry.battleSummary(
			current.number,
			battle.summaryText(),
			battle.notice(NoticeKind.BATTLE_SUMMARY),
			BattleSummary.of(told, battle.system)
		)
		val game = copy(turn = after, log = told + summary)
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
	fun landShip(email: String, location: ShipLocation?, dice: Dice): Game {
		val current = turnAwaiting(email, Turn::currentJumper, Turn::jumperWaitingText)
		current.refuseDuringFight()
		val shipName = current.shipWaitingToLand()
		if (location == null) {
			throw InvalidLandingException()
		}
		val game = withShipPlaced(email, shipName, location, dice).withTurn(current.landed())
		val landed = game.playerOf(email)
		val ship = landed.ship(shipName)
		// R80: staying in Space where other players' ships are starts a battle, with nobody intercepted.
		if (location == ShipLocation.SPACE && ship != null && players.any { it.email != email && it.shipsInSpaceAt(ship.system).isNotEmpty() }) {
			return game.withBattleStarted(ship.system, emptyList())
		}
		return if (current.haveAllJumped(landed.jumpableShips())) game.endJumpSlot(email) else game
	}

	/**
	 * This game with [email]'s ship [shipName] placed at [location] (R30, R77) and logged: on a Planet only past its
	 * security (R116, rolled with [dice]); in a Port only while Ports are open (E10).
	 */
	private fun withShipPlaced(email: String, shipName: String, location: ShipLocation, dice: Dice): Game {
		val number = runningTurn().number
		if (location == ShipLocation.PORT && news.conditions.portsClosedIn(number)) {
			throw InvalidLandingException("Ports are closed this turn (epidemics): choose the Planet or Space.")
		}
		if (location == ShipLocation.PLANET) {
			return withLandingRolled(email, shipName, dice)
		}
		val landed = playerOf(email).landShip(shipName, location)
		return copy(players = replace(landed)).withLogged(number, listOf(landed.landedLog(shipName))).withDeliveryChecked(email, shipName)
	}

	/** This game once [email]'s ship [shipName], if it just reached its delivery's Port, delivered it: paid, logged (R114). */
	private fun withDeliveryChecked(email: String, shipName: String): Game {
		val player = playerOf(email)
		val terms = player.ship(shipName)?.deliveryJob?.opportunity as? OpportunityTerms.Delivery ?: return this
		val delivered = player.delivered(shipName) ?: return this
		return withPlayerChanged(
			delivered,
			listOf("${player.corporationName} delivered ${terms.cargo} to ${terms.to.displayName}"),
			runningTurn().number
		)
	}

	/** [system]'s Planet security now (R116): the board's, plus any event's change this turn (E10, E12). */
	private fun planetSecurity(system: StarSystem): Int = system.locations.security + news.conditions.securityChangeIn(turn?.number ?: 0)

	/**
	 * [email]'s ship [shipName] tries to land on its system's Planet (R116–R118): 2d6 + its bonuses against the Planet's
	 * security this turn. Above, it lands; equal, it's forced into the Port (Space while Ports are closed), its illegal goods
	 * confiscated, the ship too if military, and its owner loses 2d6 reputation; below, the ship and everything aboard are
	 * seized and its owner loses 3d6. The roll and outcome are public; what was taken isn't.
	 */
	private fun withLandingRolled(email: String, shipName: String, dice: Dice): Game {
		val number = runningTurn().number
		val player = playerOf(email)
		val ship = checkNotNull(player.ship(shipName))
		val roll = LandingRoll(dice.twoD6(), player.landingBonusFor(shipName), planetSecurity(ship.system))
		val badger = player.badgerBonusFor(shipName).takeIf {
			it > 0
		}?.let { Agent.BADGER.actedLog(player.corporationName, ": +$it to $shipName's landing") }
		val who = "$shipName (${player.corporationName})"
		val planet = "${ship.system.displayName}'s Planet"
		return when (roll.outcome()) {
			LandingOutcome.LANDED ->
				copy(players = replace(player.landShip(shipName, ShipLocation.PLANET)))
					.withLogged(number, listOfNotNull(badger, "$who landed on $planet: ${roll.text()}."))
			LandingOutcome.FORCED_INTO_PORT -> {
				val closed = news.conditions.portsClosedIn(number)
				val forced = player.landShip(shipName, if (closed) ShipLocation.SPACE else ShipLocation.PORT).confiscateIllegalCargo(shipName)
				val kept = if (ship.isMilitary()) forced.confiscateShip(shipName) else forced
				val loss = kept.loseReputation(dice.twoD6(), "forced from the Planet by security")
				val system = ship.system.displayName
				val where = if (closed) "held in $system's Space (Ports are closed)" else "forced into $system's Port"
				val taken = if (ship.isMilitary()) "The ship was confiscated." else "Illegal cargo confiscated."
				copy(players = replace(loss.player)).withLogged(number, listOfNotNull(badger, "$who was $where: ${roll.text()}. $taken") + loss.lines)
			}
			LandingOutcome.SEIZED -> {
				val loss = player.confiscateShip(shipName).loseReputation(dice.twoD6() + dice.d6(), "seized by security")
				copy(players = replace(loss.player)).withLogged(
					number,
					listOfNotNull(badger, "$who was seized at ${ship.system.displayName}: ${roll.text()}.") + loss.lines
				)
			}
		}
	}

	/** [email] ends their jump slot (S02-6): ships they didn't jump stay where they are, and the next player's slot starts. */
	fun finishJumping(email: String): Game {
		val current = turnAwaiting(email, Turn::currentJumper, Turn::jumperWaitingText)
		current.refuseDuringFight()
		current.refuseWhileAShipWaitsToLand()
		return endJumpSlot(email)
	}

	/**
	 * [email] puts the goods still to resolve in [order] (R25, S01-3); only the first player in initiative may, at any
	 * time during Trading, and [Turn.reorderQueue] refuses an order that's gone stale. Not logged: only the resolutions are.
	 */
	fun reorderTradingQueue(email: String, order: List<MarketGood>): Game =
		copy(turn = turnAwaiting(email, Turn::resolverToDecide, Turn::resolverWaitingText).reorderQueue(order))

	/**
	 * [email] resolves the next good in the queue (R25, S01-4), which only the first player in initiative may, and only
	 * once the last good has finished resolving. Its offers are disclosed (E05-S02-1), and its Dictator at that system,
	 * if any, is asked before the roll (S02-2); with none, the counter starts with a roll from [dice] (E05-S03). All of
	 * it is logged: "Resolving: Mu Herculis ISO (chosen by Vega Lines)" (S01-5), the offers, then the counter.
	 */
	fun resolveNextGood(email: String, dice: Dice): Game {
		val current = turnAwaiting(email, Turn::resolverToDecide, Turn::resolverWaitingText)
		current.resolution?.waitingText(corporationNames())?.let { throw InvalidGameOperationException(it) }
		current.limpyAsk?.let { throw InvalidGameOperationException(current.limpyWaitingText(corporationNames())) }
		val good = current.nextGood()
		val dictator = players.firstOrNull { it.isDictatorOf(good.system, good.goods) }
		// R108: with no Dictator there, Limpy's owner in Port there is asked first, once a turn.
		val limpy = players.firstOrNull { dictator == null && it.mayDictateAt(good.system) && !current.hasUsedLimpy(it.email) }
		if (limpy != null) {
			return copy(turn = current.askLimpy(limpy.email))
		}
		return withNextGoodResolving(current, email, dictator?.email, dice)
	}

	/** [current]'s next good starting to resolve, chosen by [resolver], with [dictator] asked first if any (R25, R34, R38). */
	private fun withNextGoodResolving(current: Turn, resolver: String, dictator: String?, dice: Dice): Game {
		val good = current.nextGood()
		val wholesalers = players.filter { it.isWholesalerOf(good) }.map { it.email }
		val started = current.resolveNext(dictator, wholesalers, prices.priceOf(good), dice, supplyDemandShifts)
		val resolution = checkNotNull(started.resolution)
		return withResolutionMoved(
			started,
			null,
			listOf(resolution.resolvingText(resolver, corporationNames()), resolution.disclosure(corporationNames()))
		)
	}

	/**
	 * [email], asked about Limpy (R108), [acts] as Dictator of the next good for its resolution, or passes and keeps him for
	 * another good this turn. Acting replaces their own offer on that good and reveals Limpy.
	 */
	fun answerLimpy(email: String, acts: Boolean, dice: Dice): Game {
		val current = turnAwaiting(email, Turn::limpyToDecide, Turn::limpyWaitingText)
		val good = current.nextGood()
		val answered = current.limpyAnswered(acts)
		if (!acts) {
			return withNextGoodResolving(answered, current.firstInInitiative(), null, dice)
		}
		val line = LogEntry.event(current.number, Agent.LIMPY.actedLog(corporationOf(email), " lets them act as Dictator of ${good.label()}"))
		return copy(log = log + line).withNextGoodResolving(answered.withoutOfferOf(email, good), current.firstInInitiative(), email, dice)
	}

	/**
	 * [email], the Dictator asked, places their one offer for the good resolving (R38, S02-2): checked like any offer
	 * (R03), then disclosed like the others (S02-3) and logged: "Altair Trade Co. (Dictator) offers to sell at 10 HT".
	 * Then the counter starts with a roll from [dice] (E05-S03).
	 */
	fun placeDictatorOffer(email: String, type: OfferType, price: Int?, dice: Dice): Game {
		val current = turnAwaiting(email, Turn::dictatorToDecide, Turn::dictatorWaitingText)
		val offer = playerOf(email).dictatorOffer(checkNotNull(current.resolution).good, type, price)
		val decided = current.placeDictatorOffer(offer, dice)
		return withResolutionMoved(
			decided,
			current.resolution,
			listOf(checkNotNull(current.resolution).dictatorOfferText(offer, corporationNames()))
		)
	}

	/**
	 * [email], the Dictator asked, passes (S02-2); logged: "Altair Trade Co. (Dictator) passed". Then the counter starts
	 * with a roll from [dice] (E05-S03).
	 */
	fun passAsDictator(email: String, dice: Dice): Game {
		val current = turnAwaiting(email, Turn::dictatorToDecide, Turn::dictatorWaitingText)
		val decided = current.dictatorPassed(dice)
		return withResolutionMoved(decided, current.resolution, listOf(checkNotNull(current.resolution).dictatorPassedText(corporationNames())))
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
		val current = turnAwaiting(email, Turn::traderToDecide, Turn::traderWaitingText)
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
			return PositionRoll(roll, null, emptyList(), null)
		}
		val tier = trader.promotionAt(good)
		val demoted = if (tier == MarketTier.DICTATOR) {
			players.filter { it.email != trader.email && it.isDictatorOf(good) }.map { it.email }
		} else {
			emptyList()
		}
		return PositionRoll(roll, tier, demoted, trader.businessAfterMarketRise())
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
		val current = turnAwaiting(email, Turn::traderToDecide, Turn::traderWaitingText)
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
		val current = turnAwaiting(email, Turn::wholesalerToDecide, Turn::wholesalerWaitingText)
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
		val current = turnAwaiting(email, Turn::wholesalerToDecide, Turn::wholesalerWaitingText)
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
			log = log + events.map { LogEntry.event(after.number, it) } + resolutionLog(after.number, before, resolution)
		)
		return if (after.isEveryGoodResolved()) moved.endTrading(after) else moved
	}

	/** The public log of what [resolution] did in turn [turn] since [before] ([Resolution.logSince]), with the reputation each rise moved. */
	private fun resolutionLog(turn: Int, before: Resolution?, resolution: Resolution): List<LogEntry> =
		resolution.logSince(before, corporationNames()).flatMap { line ->
			listOf(LogEntry.trade(turn, line.text, line.trade)) + line.rise?.let { marketRiseLog(it) }.orEmpty().map { LogEntry.event(turn, it) }
		}

	/** R86 (E13-S02): a position rise is +2 reputation for its trader and −2 for each Dictator it demoted, logged after the roll. */
	private fun marketRiseLog(rise: MarketRise): List<String> =
		listOf(playerOf(rise.riser).reputationLog(Player.MARKET_RISE_REPUTATION, "a market position rise")) +
			rise.demoted.flatMap { email ->
				val demoted = playerOf(email)
				val loss = demoted.reputationLossAfterAgents(Player.MARKET_RISE_REPUTATION)
				listOf(demoted.reputationLog(-loss, "losing a market position", Player.MARKET_RISE_REPUTATION - loss))
			}

	/**
	 * The running turn, once checked that its step waits for [email]: [awaited] says whom it waits for, and anyone else is
	 * refused with [waitingText] (R04: an ordered step out of turn is refused, naming whom it waits for).
	 */
	private fun turnAwaiting(email: String, awaited: (Turn) -> String, waitingText: (Turn, CorporationNames) -> String): Turn {
		playerOf(email)
		val current = runningTurn()
		if (awaited(current) != email) {
			throw NotYourTurnException(waitingText(current, corporationNames()))
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
			log = log + LogEntry.event(current.number, "${corporationOf(jumper)} has no ship to jump")
		).withEmptyJumpSlotsSkipped()
	}

	/**
	 * Bidding → Ship-jumps (R27): every player pays their bid, win or lose, and a player who never bid counts as
	 * bidding 0 and still rolls (Q07, proposed). Every bid and roll is public from now on and logged in initiative order
	 * (creator, 2026-11-18): "Initiative (bid + 2d6): Vega Lines 40 + 11 = 51 → Orion Freight 0 + 3 = 3", then any tie's
	 * rolls: " (tie rolled again: Vega Lines 9, Orion Freight 4)".
	 */
	private fun closeBidding(current: Turn, dice: Dice): Game {
		val bonuses = players.associate { it.email to it.initiativeBonus() }.filterValues { it > 0 }
		val closed = current.closeBidding(players.map { it.email }, bonuses, dice)
		val wolf = bonuses.keys.map { Agent.WOLF.actedLog(corporationOf(it), ": +${Agent.WOLF_INITIATIVE_BONUS} initiative") }
		return copy(
			players = players.map { it.pay(current.bidAmountOf(it.email), "Initiative bid") },
			turn = closed,
			log = log + (wolf + closed.initiativeLog(corporationNames())).map { LogEntry.event(current.number, it) }
		)
	}

	/** The player whose membership key is [email]; refused when they aren't in this game. */
	private fun playerOf(email: String): Player = players.firstOrNull { it.email == email } ?: throw NotAGameMemberException()

	private fun replace(player: Player): List<Player> = players.map { if (it.email == player.email) player else it }

	private fun corporationOf(email: String): String = corporationNames().of(email)

	/** Every player's corporation by email: how a part names players in a log line or a refusal ([CorporationNames]). */
	private fun corporationNames(): CorporationNames = CorporationNames(players.associate { it.email to it.corporationName })

	private fun runningTurn(): Turn = when (status) {
		GameStatus.LOBBY -> throw InvalidGameOperationException("This game hasn't started")
		GameStatus.FINISHED -> throw InvalidGameOperationException("This game is over")
		GameStatus.RUNNING -> checkNotNull(turn) { "A running game always has a turn" }
	}

	companion object {
		/** E1: what the psionic field adds to every jump's target this turn. */
		const val PSIONIC_JUMP_MODIFIER = -4

		/** E12: a d6 above this lets a ship in Port escape the pirates. */
		const val PIRATE_ESCAPE_ABOVE = 3

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
		/** A new game of [turns] turns created by [creator], with [news] as its tip-offs: the full pool in play, none in tests (R109). */
		fun lobby(id: String, turns: Int?, creator: Player, news: News = News.NONE): Game {
			if (turns == null || turns !in MIN_TURNS..MAX_TURNS) {
				throw InvalidGameOperationException("Enter a whole number of turns from $MIN_TURNS to $MAX_TURNS.")
			}
			return Game(
				id, turns, GameStatus.LOBBY, creator.email,
				listOf(
					creator
				),
				emptyList(), null, MarketPrices.board(), emptyList(), emptyList(), news
			)
		}
	}
}

/** 1st, 2nd, 3rd, 4th … for a place in the turn order (players are at most six, so no 11th–13th). */
