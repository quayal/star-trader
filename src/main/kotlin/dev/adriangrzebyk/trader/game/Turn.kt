package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.agent.AgentOffer
import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import dev.adriangrzebyk.trader.game.battle.BattleReport
import dev.adriangrzebyk.trader.game.battle.Interception
import dev.adriangrzebyk.trader.game.sabotage.Sabotage
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget

/**
 * The turn a running game is in and its phase (R41: a game starts in turn 1, Bidding).
 * [doneBidding] lists, in signal order, the players who have signalled done during this turn's Bidding;
 * it's public, so everyone can see who the game is waiting for (D09), and empty outside Bidding.
 * [offers] are this turn's trade offers in the order they were placed. Each is secret to its player until Trading
 * starts (R17), so nothing outside a player's own view may carry them before; they stay through Trading and the next
 * turn starts with none.
 * [bids] are this turn's initiative bids, at most one per player and each secret to its player (R29); like offers
 * they stay for the whole turn. [initiative] is every player's email in initiative order (R27), public, set when
 * Bidding closes and empty before. [turnOrderPicks] are the places players picked in this turn's turn order (R26), in
 * pick order; public, and empty until the first pick. [jumpedShips] names the ships that jumped this turn (each may
 * jump once, R26; names are unique in a game) and [doneJumping] the players whose jump slot has ended, in order; both
 * public and empty until the first jump. [landingShip] is the ship that jumped and whose player hasn't chosen where it
 * ends up yet (R30, S03); public, and null when no ship waits to land. [doneInvesting] lists, in signal order, the
 * players who have signalled done during this turn's Investment (R44, E06-S01-2); like [doneBidding] it's public, so
 * everyone can see who the game is waiting for (D09), and empty outside Investment. [collected] lists, in confirm order,
 * the players with factories who have confirmed where this turn's output goes (R47, E06-S03-5); public like
 * [doneInvesting], and empty outside Investment. [interception] is the arrival other players decide whether to intercept
 * (R70), and [battle] the battle it started (R73); while either is set, nobody jumps or lands. [battleReport] is what
 * the last battle round's salvos did (R75–R78), kept until the next round fires or a new battle starts, so it outlives
 * the battle. [connectionsRaised] lists the players who raised a connection this Investment (R83, E12-S02), once each;
 * public, and empty outside Investment. [reputationBought] lists those who bought reputation this Investment (R87, E13-S03),
 * once each, likewise. [interceptionTries] are every interception try this turn, successful or not (R70), public: a battle
 * started later in the turn at the same system still knows who tried whom first (R86). [initiativeRolls] are every
 * player's bid and 2d6 rolls for initiative, in initiative order, public once Bidding closes and empty before (R27,
 * creator 2026-11-18).
 * [doneOpportunity] lists, in order, the players whose Opportunity turn has ended (R102); the player on turn is the first
 * in the turn order who isn't in it. [sabotages] are this Opportunity's sabotages (R97–R101), [recruitedBy] who recruited
 * (R103) and [agentOffers] the agents offered for sale and not yet answered (R106); all emptied when Trading closes into
 * Opportunity. [interestPaid] lists who paid loan interest this Investment (R92). [limpyAsk] is the player asked whether
 * Limpy lets them act as Dictator of the next good (R108), and [limpyUsedBy] who has used him this turn. [doneNews] lists
 * who has signalled done with the news (R109), public like [doneBidding].
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
	val resolution: Resolution?,
	val doneInvesting: List<String>,
	val collected: List<String>,
	val interception: Interception?,
	val battle: Battle?,
	val battleReport: BattleReport?,
	val connectionsRaised: List<String>,
	val reputationBought: List<String>,
	val interceptionTries: List<BattleInterception>,
	val initiativeRolls: List<InitiativeRoll>,
	val doneOpportunity: List<String>,
	val sabotages: List<Sabotage>,
	val recruitedBy: List<String>,
	val agentOffers: List<AgentOffer>,
	val interestPaid: List<String>,
	val limpyAsk: String?,
	val limpyUsedBy: List<String>,
	val doneNews: List<String> = emptyList()
) {

	/** [email]'s own offers this turn: the only offers a player may be shown before Trading starts (R17, A05). */
	fun offersOf(email: String): List<Offer> = offers.filter { it.playerEmail == email }

	/**
	 * [email]'s offers whose good has finished resolving with no trade of theirs among [trades] (this turn's): each gets a card
	 * to acknowledge (playtest, 2026-11-20), whether it lost or won and couldn't trade.
	 */
	fun untradedOffersOf(email: String, trades: List<TradeRecord>): List<Offer> =
		offersOf(email).filter { offer -> resolvedGoods.any { offer.isFor(it) } && trades.none { it.isFor(offer) } }

	/** Every offer of this turn once Trading has started, when they become public to all players (R17); none before. */
	fun publicOffers(): List<Offer> = if (phase >= TurnPhase.TRADING) offers else emptyList()

	/** [email]'s own initiative bid this turn, or null if they haven't placed one: the only bid they may see (R29). */
	fun bidOf(email: String): InitiativeBid? = bids.firstOrNull { it.playerEmail == email }

	/**
	 * The turn order's places, first to last, each holding the email of the player who picked it or null while it's
	 * free. It has a place per player once Bidding has closed (one per initiative entry) and none before.
	 */
	fun turnOrder(): List<String?> = List(initiative.size) { index -> turnOrderPicks.firstOrNull { it.place == index + 1 }?.playerEmail }

	/** Whose pick it is (R26): the first player in initiative order without a place, or null when nobody is picking. */
	fun picker(): String? {
		if (phase != TurnPhase.INITIATIVE) {
			return null
		}
		return initiative.firstOrNull { email -> turnOrderPicks.none { it.playerEmail == email } }
	}

	/**
	 * Whose jump slot it is (R26, S02): the first player in the turn order whose slot hasn't ended, or null after the last
	 * slot and outside Jumps.
	 */
	fun jumper(): String? {
		if (phase != TurnPhase.JUMPS || initiative.isEmpty()) {
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

	/** Refuses anything but Jumps: before it, it hasn't started; after it, it has closed (A04). */
	fun requireJumps() {
		requirePhase(TurnPhase.JUMPS)
	}

	/** Refuses anything but Initiative, where the turn order is picked (R26): before it, it hasn't started; after, it has closed. */
	fun requireInitiative() {
		requirePhase(TurnPhase.INITIATIVE)
	}

	/** Refuses anything but News (R109). */
	fun requireNews() {
		requirePhase(TurnPhase.NEWS)
	}

	private fun requirePhase(wanted: TurnPhase) {
		when {
			phase < wanted -> throw InvalidGameOperationException("${wanted.displayName} hasn't started.")
			phase > wanted -> throw PhaseClosedException(wanted)
		}
	}

	/** This turn after [email] signalled done with the news (R109); a repeat changes nothing. */
	fun signalDoneNews(email: String): Turn {
		requireNews()
		return if (email in doneNews) this else copy(doneNews = doneNews + email)
	}

	/** Whether everyone in [emails] has signalled done with the news, so Jumps can start (R109). */
	fun everyoneDoneWithNews(emails: List<String>): Boolean = emails.all { it in doneNews }

	/** Initiative → News (R109): nobody has read it yet. */
	fun toNews(): Turn = copy(phase = TurnPhase.NEWS, doneNews = emptyList())

	/** News → Jumps (R44): the first player in the turn order jumps first (R26). */
	fun toJumps(): Turn = copy(phase = TurnPhase.JUMPS)

	/** This turn with every place still free taken by the players without one, in initiative order (R26, for [Game.advancePhase]). */
	fun withPlacesFilled(): Turn = generateSequence(this) { turn ->
		turn.picker()?.let { picker -> turn.pickPlace(picker, turn.turnOrder().indexOfFirst { it == null } + 1) }
	}.last()

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

	/**
	 * This turn without [email]'s offer on [goods] at [system] (R59); the same turn when they have none, so a second
	 * withdrawal of the same offer changes nothing. Other players' offers are never touched.
	 */
	fun withdrawOffer(email: String, system: StarSystem, goods: Goods): Turn {
		requireBidding()
		return copy(offers = offers.filterNot { it.playerEmail == email && it.system == system && it.goods == goods })
	}

	/** What [email] pays when Bidding closes (R27): their bid, or 0 if they never placed one (Q07, proposed). */
	fun bidAmountOf(email: String): Int = bidOf(email)?.amount ?: 0

	/**
	 * The public log line of the initiative once Bidding closed, in initiative order (creator, 2026-11-18): "Initiative
	 * (bid + 2d6): Vega Lines 40 + 11 = 51 → Orion Freight 0 + 3 = 3", then any tie's rolls: " (tie rolled again: Vega
	 * Lines 9, Orion Freight 4)".
	 */
	fun initiativeLog(names: CorporationNames): String {
		val order = initiativeRolls.joinToString(" → ") { it.logText(names.of(it.playerEmail)) }
		val ties = initiativeRolls.mapNotNull { it.tieLogText(names.of(it.playerEmail)) }
		val tieText = if (ties.isEmpty()) "" else " (tie rolled again: ${ties.joinToString(", ")})"
		return "Initiative (bid + 2d6): $order$tieText"
	}

	/**
	 * This turn after [email] picked [place] (1 = first) in the turn order (R26). A place that isn't free is refused.
	 * When only one place is left, its player gets it without being asked (S01-6, proposed). Checking that it's
	 * [email]'s pick is the caller's, which knows the players' names.
	 */
	fun pickPlace(email: String, place: Int?): Turn {
		requireInitiative()
		val order = turnOrder()
		if (place == null || place !in 1..order.size || order[place - 1] != null) {
			throw InvalidPlaceException()
		}
		return withPick(TurnOrderPick(email, place)).withLastPlaceFilled()
	}

	/** Whose pick it is, once checked that this is Ship-jumps and the turn order isn't set yet (R26). */
	fun pickerToDecide(): String {
		requireInitiative()
		return picker() ?: throw InvalidGameOperationException("The turn order is already set.")
	}

	/** Refusing a pick out of turn (R04): "It's Vega Lines's turn to choose a place." */
	fun pickerWaitingText(names: CorporationNames): String = "It's ${names.of(pickerToDecide())}'s turn to choose a place."

	/**
	 * The public log lines of the picks after the first [picksBefore] (S01-5): the picker's own ("Vega Lines chose 1st"),
	 * then a last place filled without asking ("Orion Freight gets 2nd, the only place left"), and once every place is
	 * taken, the full order.
	 */
	fun turnOrderLog(picksBefore: Int, names: CorporationNames): List<String> {
		val picks = turnOrderPicks.drop(picksBefore).mapIndexed { index, pick ->
			val corporation = names.of(pick.playerEmail)
			if (index == 0) "$corporation chose ${ordinal(pick.place)}" else "$corporation gets ${ordinal(pick.place)}, the only place left"
		}
		val order = if (picker() ==
			null
		) {
			listOf("Turn order (for this turn): " + turnOrder().joinToString(" → ") { names.of(checkNotNull(it)) })
		} else {
			emptyList()
		}
		return picks + order
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
		requireJumps()
		// Only a game saved before initiative existed (E03-S05) can be in Jumps with nobody to jump.
		return jumper() ?: throw InvalidGameOperationException("Nobody is jumping now.")
	}

	/** Refusing a jump out of turn (R04): "It's Vega Lines's turn to jump." */
	fun jumperWaitingText(names: CorporationNames): String = "It's ${names.of(currentJumper())}'s turn to jump."

	/** S02-3, S03-6: a jump isn't finished until its ship has landed, so nothing else happens in the slot until then. */
	fun refuseWhileAShipWaitsToLand() {
		landingShip?.let { throw InvalidGameOperationException("Choose where $it ends up first.") }
	}

	/** R73: while an interception is being decided or a battle lasts, the jumps wait. */
	fun refuseDuringFight() {
		interception?.let { throw InvalidGameOperationException("Waiting for the decision on intercepting ${it.ship}.") }
		battle?.let { throw InvalidGameOperationException("A battle at ${it.system.displayName} is under way.") }
	}

	/** The interception being decided (R70), refused when there is none. */
	fun interceptionNow(): Interception {
		requireJumps()
		return interception ?: throw InvalidGameOperationException("No interception is being decided.")
	}

	/** This turn with [changed] as its interception, or none. */
	fun withInterception(changed: Interception?): Turn = copy(interception = changed)

	/** The battle under way (R73), refused when there is none. */
	fun battleNow(): Battle {
		requireJumps()
		return battle ?: throw InvalidGameOperationException("No battle is under way.")
	}

	/** This turn with [changed] as its battle, or none. */
	fun withBattle(changed: Battle?): Turn = copy(battle = changed)

	/** This turn with [started] as its battle and no report from an earlier one (R73). */
	fun withBattleStarted(started: Battle): Turn = copy(battle = started, battleReport = null)

	/** This turn once a battle round fired (R74–R78): [changed] is the battle after it, [report] what its salvos did. */
	fun withBattleRound(changed: Battle, report: BattleReport): Turn = copy(battle = changed, battleReport = report)

	/** This turn with [changed] as its battle once [ships]' hits were placed, destroying those in [destroyed] (R76). */
	fun withHitsPlaced(changed: Battle, ships: Set<String>, destroyed: Set<String>): Turn =
		copy(battle = changed, battleReport = battleReport?.hitsPlaced(ships, destroyed))

	/**
	 * This turn after [tries] (R72): like a jump, each is the trying ship's move for the turn, and it's remembered for any
	 * battle later this turn (R86).
	 */
	fun recordInterceptionTries(tries: List<BattleInterception>): Turn = copy(
		jumpedShips = jumpedShips + tries.map { it.interceptor }.filterNot { it in jumpedShips },
		interceptionTries = interceptionTries + tries
	)

	/** Whether [ship] has already jumped this turn (each ship jumps at most once, R26). */
	fun hasJumped(ship: Ship): Boolean = ship.name in jumpedShips

	/** Whether every one of [ships] has jumped this turn. */
	fun haveAllJumped(ships: List<Ship>): Boolean = ships.all { hasJumped(it) }

	/** This turn after [ship] jumped: it can't jump again, and it waits to land (S03). */
	fun recordJump(ship: Ship): Turn = copy(jumpedShips = jumpedShips + ship.name, landingShip = ship.name)

	/** This turn after [ship]'s jump failed (R68): it can't jump again, and stays where it was, so nothing waits to land. */
	fun recordFailedJump(ship: Ship): Turn = copy(jumpedShips = jumpedShips + ship.name)

	/** The name of the ship waiting to land (S03), refused when there is none. */
	fun shipWaitingToLand(): String = landingShip ?: throw InvalidGameOperationException("No ship is waiting to land.")

	/** This turn after the ship waiting to land has landed. */
	fun landed(): Turn = copy(landingShip = null)

	/** This turn after [email]'s jump slot ended; after the last one the phase moves to Trading (S02-8). */
	fun endJumpSlot(email: String): Turn {
		val ended = copy(doneJumping = doneJumping + email)
		return if (ended.jumper() == null) ended.toTrading() else ended
	}

	/**
	 * Bidding → Initiative (R27) with each player's initiative [bonuses] (Wolf, R108) added to their bid + 2d6; then the
	 * players pick their places in the turn order (R26).
	 */
	fun closeBidding(emails: List<String>, bonuses: Map<String, Int>, dice: Dice): Turn {
		val rolls = initiativeOrder(
			emails.map { InitiativeRoll(it, bidAmountOf(it), bonuses[it] ?: 0, listOf(dice.twoD6())) },
			InitiativeRoll::total,
			dice
		)
		return copy(phase = TurnPhase.INITIATIVE, doneBidding = emptyList(), initiative = rolls.map { it.playerEmail }, initiativeRolls = rolls)
	}

	/** Jumps → Trading (N06): every good at every system is queued to resolve (R36, S01-2). */
	fun toTrading(): Turn =
		copy(phase = TurnPhase.TRADING, tradingQueue = MarketGood.everyOnBoard(), resolvedGoods = emptyList(), resolution = null)

	/** Trading → Opportunity (R44, R102): nobody has collected or acted yet, and the first player in the turn order is on turn. */
	fun endTrading(): Turn = copy(
		phase = TurnPhase.OPPORTUNITY,
		collected = emptyList(),
		doneOpportunity = emptyList(),
		sabotages = emptyList(),
		recruitedBy = emptyList(),
		agentOffers = emptyList()
	)

	/** Refuses anything but Opportunity (R102). */
	fun requireOpportunity() {
		if (phase != TurnPhase.OPPORTUNITY) {
			throw InvalidGameOperationException("Opportunity isn't open.")
		}
	}

	/**
	 * Whose Opportunity turn it is (R102): the first player in the turn order (R26) whose turn hasn't ended, or null outside
	 * Opportunity and once everyone is done.
	 */
	fun opportunist(): String? {
		if (phase != TurnPhase.OPPORTUNITY) {
			return null
		}
		return turnOrder().filterNotNull().ifEmpty { initiative }.firstOrNull { it !in doneOpportunity }
	}

	/** Whose Opportunity turn it is, refused outside Opportunity and once everyone is done (R102). */
	fun currentOpportunist(): String {
		requireOpportunity()
		return opportunist() ?: throw InvalidGameOperationException("Everyone is done with opportunities.")
	}

	/** Refusing an Opportunity action out of turn (R04, R102): "It's Vega Lines's Opportunity turn." */
	fun opportunistWaitingText(names: CorporationNames): String = "It's ${names.of(currentOpportunist())}'s Opportunity turn."

	/** This turn after [email]'s Opportunity turn ended (R102): the next player in the turn order is on. */
	fun endOpportunityTurn(email: String): Turn = copy(doneOpportunity = doneOpportunity + email)

	/** Whether Opportunity can close (R102): everyone is done, and no choice owed out of turn, like a ship's hits, is open. */
	fun isOpportunityOver(): Boolean = phase == TurnPhase.OPPORTUNITY && opportunist() == null && sabotages.none { it.awaitsHits() }

	/** Opportunity → Investment (R44): nobody done investing yet, and agent offers nobody answered expire (R106). */
	fun toInvestment(): Turn = copy(
		phase = TurnPhase.INVESTMENT,
		doneInvesting = emptyList(),
		connectionsRaised = emptyList(),
		reputationBought = emptyList(),
		interestPaid = emptyList(),
		agentOffers = emptyList()
	)

	/** The players who owe a choice out of turn before Opportunity can close (R102): ship owners with sabotage hits to place. */
	fun owedChoices(): List<String> = sabotages.filter { it.awaitsHits() }.map { it.target.ownerEmail }.distinct()

	/** Whether [email] has sabotaged this Opportunity: once per Opportunity (R97). */
	fun hasSabotaged(email: String): Boolean = sabotages.any { it.saboteurEmail == email }

	/** This turn with [sabotage] recorded (R97): once per Opportunity per player. */
	fun recordSabotage(sabotage: Sabotage): Turn {
		requireOpportunity()
		if (hasSabotaged(sabotage.saboteurEmail)) {
			throw InvalidGameOperationException("You've already sabotaged this Opportunity.")
		}
		return copy(sabotages = sabotages + sabotage)
	}

	/** [email]'s sabotage waiting for them to choose a warehouse's missing units (R99), or null. */
	fun lossChoiceOf(email: String): Sabotage? = sabotages.firstOrNull { it.awaitsLossChoiceBy(email) }

	/** The sabotage hits waiting for [email] to place them on [shipName] (R99), refused when there are none. */
	fun sabotageHitsOn(email: String, shipName: String): Sabotage =
		sabotages.firstOrNull { it.awaitsHitsFrom(email) && (it.target as? SabotageTarget.Ship)?.name == shipName }
			?: throw InvalidGameOperationException("$shipName has no sabotage hits to place.")

	/** The sabotages whose hits [email] still has to place (R99). */
	fun sabotageHitsOwedBy(email: String): List<Sabotage> = sabotages.filter { it.awaitsHitsFrom(email) }

	/** This turn with [before] replaced by [after]. */
	fun withSabotageChanged(before: Sabotage, after: Sabotage): Turn = copy(sabotages = sabotages.map { if (it == before) after else it })

	/** The sabotages [email] ordered this Opportunity (R101): theirs to see with the rolls. */
	fun sabotagesBy(email: String): List<Sabotage> = sabotages.filter { it.saboteurEmail == email }

	/** The sabotages on [email]'s assets this Opportunity (R101): what happened, never who did it. */
	fun sabotagesOn(email: String): List<Sabotage> = sabotages.filter { it.target.ownerEmail == email && it.saboteurEmail != email }

	/** Refuses [email]'s done signal while a choice of their own sabotage is open (R99). */
	fun refuseDoneWhileChoosing(email: String) {
		lossChoiceOf(email)?.let { throw InvalidGameOperationException("Choose the units missing from the warehouse first.") }
	}

	/** Whether [email] has recruited this Opportunity: once per Opportunity (R103). */
	fun hasRecruited(email: String): Boolean = email in recruitedBy

	/** This turn after [email] tried to recruit (R103), refused the second time. */
	fun recruitedByPlayer(email: String): Turn {
		requireOpportunity()
		if (hasRecruited(email)) {
			throw InvalidGameOperationException("You've already recruited this Opportunity.")
		}
		return copy(recruitedBy = recruitedBy + email)
	}

	/** This turn with [offer]'s agent offered (R106), replacing an earlier offer of the same agent; [offer]'s id is made here. */
	fun withAgentOffered(offer: AgentOffer): Turn {
		requireOpportunity()
		val id = (agentOffers.maxOfOrNull { it.id } ?: 0) + 1
		return copy(agentOffers = agentOffers.filterNot { it.agent == offer.agent } + offer.withId(id))
	}

	/** The agent offer [id], refused when it's gone (answered, or expired when Opportunity closed). */
	fun agentOffer(id: Int?): AgentOffer {
		requireOpportunity()
		return agentOffers.firstOrNull { it.id == id } ?: throw InvalidGameOperationException("This offer is no longer open.")
	}

	/** This turn once the agent offer [offer] was answered. */
	fun withoutAgentOffer(offer: AgentOffer): Turn = copy(agentOffers = agentOffers.filterNot { it.id == offer.id })

	/** The agent offers [email] made or may answer (R106). */
	fun agentOffersConcerning(email: String): List<AgentOffer> = agentOffers.filter { it.concerns(email) }

	/** Whether [email] has paid loan interest this Investment (R92). */
	fun hasPaidInterest(email: String): Boolean = email in interestPaid

	/** This turn after [email] paid loan interest (R92): once per Investment. */
	fun interestPaidBy(email: String): Turn {
		requireInvesting()
		if (hasPaidInterest(email)) {
			throw InvalidGameOperationException("You've already paid this turn's interest.")
		}
		return copy(interestPaid = interestPaid + email)
	}

	/** This turn asking [email] whether Limpy lets them act as Dictator of the next good (R108). */
	fun askLimpy(email: String): Turn = copy(limpyAsk = email)

	/** Whom the Limpy question waits for, refused when nobody is asked (R108). */
	fun limpyToDecide(): String {
		requireTrading()
		return limpyAsk ?: throw InvalidGameOperationException("Nobody is asked about Limpy.")
	}

	/** Refusing the Limpy answer to anyone else: "Waiting for Vega Lines to decide on Limpy." */
	fun limpyWaitingText(names: CorporationNames): String = "Waiting for ${names.of(limpyToDecide())} to decide on Limpy."

	/** This turn once the Limpy question was answered, [used] or not (R108): used, it's gone for the turn. */
	fun limpyAnswered(used: Boolean): Turn {
		val asked = limpyToDecide()
		return copy(limpyAsk = null, limpyUsedBy = if (used) limpyUsedBy + asked else limpyUsedBy)
	}

	/** Whether [email] has used Limpy this turn: one good a turn (R108). */
	fun hasUsedLimpy(email: String): Boolean = email in limpyUsedBy

	/** This turn without [email]'s own offer on [good] (R108): acting as its Dictator replaces it. */
	fun withoutOfferOf(email: String, good: MarketGood): Turn =
		copy(offers = offers.filterNot { it.playerEmail == email && it.system == good.system && it.goods == good.goods })

	/** Refuses anything but Investment: before it, it hasn't started, and once it has closed the turn has moved on (A04). */
	fun requireInvesting() {
		if (phase != TurnPhase.INVESTMENT) {
			throw InvalidGameOperationException("Investment isn't open.")
		}
	}

	/** This turn after [email] signalled done investing (R44: a readiness flag, so a repeat changes nothing). */
	fun signalDoneInvesting(email: String): Turn {
		requireInvesting()
		return if (email in doneInvesting) this else copy(doneInvesting = doneInvesting + email)
	}

	/** Whether [email] has confirmed where this turn's output goes (R47, E06-S03-5). */
	fun hasCollected(email: String): Boolean = email in collected

	/** Whether it's Opportunity and [email] hasn't confirmed their collect yet: they may fill in their placements (R47, R102). */
	fun isCollecting(email: String): Boolean = phase == TurnPhase.OPPORTUNITY && !hasCollected(email)

	/**
	 * This turn after [email] confirmed where their output goes (R47, R102), in Opportunity. Once: a second confirm is refused, so
	 * the output can't be stored twice (A04).
	 */
	fun collectedBy(email: String): Turn {
		requireOpportunity()
		if (hasCollected(email)) {
			throw InvalidGameOperationException("You have already collected your output.")
		}
		return copy(collected = collected + email)
	}

	/** Whether [email] has raised a connection this Investment: once per Investment (R83). */
	fun hasRaisedConnection(email: String): Boolean = email in connectionsRaised

	/** This turn after [email] raised a connection (R83, E12-S02-4): once per Investment, whatever the kind. */
	fun connectionRaisedBy(email: String): Turn {
		requireInvesting()
		if (hasRaisedConnection(email)) {
			throw InvalidGameOperationException("You've already raised a connection this Investment.")
		}
		return copy(connectionsRaised = connectionsRaised + email)
	}

	/** Whether [email] has bought reputation this Investment: once per Investment (R87). */
	fun hasBoughtReputation(email: String): Boolean = email in reputationBought

	/** This turn after [email] bought reputation (R87, E13-S03): once per Investment. */
	fun reputationBoughtBy(email: String): Turn {
		requireInvesting()
		if (hasBoughtReputation(email)) {
			throw InvalidGameOperationException("You've already bought reputation this Investment.")
		}
		return copy(reputationBought = reputationBought + email)
	}

	/** Whether every one of [emails] has signalled done investing (D09). */
	fun everyoneDoneInvesting(emails: List<String>): Boolean = emails.all { it in doneInvesting }

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

	/** Who orders the queue and resolves each good, once checked that it's Trading (R25). */
	fun resolverToDecide(): String {
		requireTrading()
		return firstInInitiative()
	}

	/** Refusing the resolver's step to anyone else (R04): "Order decided by Vega Lines (first in initiative)." */
	fun resolverWaitingText(names: CorporationNames): String = "Order decided by ${names.of(firstInInitiative())} (first in initiative)."

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
	fun resolveNext(
		dictator: String?,
		wholesalers: List<String>,
		startPrice: Int,
		dice: Dice,
		shifts: SupplyDemandShifts = SupplyDemandShifts.NONE
	): Turn {
		requireTrading()
		if (resolution?.isFinished() == false) {
			throw InvalidGameOperationException("${resolution.good.label()} hasn't finished resolving.")
		}
		val good = nextGood()
		return copy(tradingQueue = tradingQueue.drop(1)).withResolution(
			Resolution.start(good, startPrice, offers, dictator, wholesalers, initiative, shifts.indicatorOf(good)),
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

	/** Refusing the Dictator's step to anyone else (R04, S01-8). */
	fun dictatorWaitingText(names: CorporationNames): String = checkNotNull(resolution).waitingForDictatorText(names)

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

	/** The player whose offer the counter waits for (E05-S04), once checked that one is. */
	fun traderToDecide(): String = tradeAwaited().traderEmail()

	/** Refusing the awaited trader's step to anyone else (R04, S01-8). */
	fun traderWaitingText(names: CorporationNames): String = tradeAwaited().waitingText(names)

	/**
	 * This turn after the awaited trade's player traded [units] (E05-S04) and made [positionRoll], if any (R43); once
	 * nothing is left, its good counts as resolved.
	 */
	fun recordTrade(units: Int, positionRoll: PositionRoll?): Turn = withResolution(resolutionNow().withTrade(units, positionRoll))

	/**
	 * Whom the wholesale step waits for to trade or pass (R37, S05-4), refused when nobody is asked. Checking that it's the
	 * caller is the caller's, which knows the players' names.
	 */
	fun wholesalerToDecide(): String {
		requireTrading()
		return resolution?.awaitingWholesaler() ?: throw InvalidGameOperationException("No wholesale decision is pending.")
	}

	/** Refusing the wholesale step to anyone but the wholesaler asked (R04, S01-8). */
	fun wholesalerWaitingText(names: CorporationNames): String = checkNotNull(resolution).waitingForWholesalerText(names)

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

	/** Whether this turn's Trading is over (E05-S07-1): nothing left in the queue, and the last good has finished resolving. */
	fun isEveryGoodResolved(): Boolean = phase == TurnPhase.TRADING && tradingQueue.isEmpty() && resolution?.isFinished() == true

	/** The start of the turn after this one. */
	fun next(): Turn = bidding(number + 1)

	/**
	 * [rolls] ordered by [score], highest first (R27). Players who tie roll 2d6 again among themselves, and only those
	 * rolls order them, until no tie is left; each reroll is kept on its player's roll. Rolls are taken in [rolls]' order.
	 */
	private fun initiativeOrder(rolls: List<InitiativeRoll>, score: (InitiativeRoll) -> Int, dice: Dice): List<InitiativeRoll> =
		rolls.groupBy(score).toSortedMap(reverseOrder()).values.flatMap { tied ->
			if (tied.size == 1) tied else initiativeOrder(tied.map { it.withTieRoll(dice.twoD6()) }, InitiativeRoll::latestRoll, dice)
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
			null,
			emptyList(),
			emptyList(),
			null,
			null,
			null,
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			null,
			emptyList()
		)
	}
}

/** A place in the turn order for the log: 1st, 2nd, 3rd, 4th… */
private fun ordinal(place: Int): String = place.toString() + when (place) {
	1 -> "st"
	2 -> "nd"
	3 -> "rd"
	else -> "th"
}
