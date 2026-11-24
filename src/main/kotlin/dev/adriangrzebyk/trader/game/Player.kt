package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentCount
import dev.adriangrzebyk.trader.game.agent.AgentPlace
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.InvalidAgentException
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import dev.adriangrzebyk.trader.game.control.ControlResult
import dev.adriangrzebyk.trader.game.ledger.MoneyLedger
import dev.adriangrzebyk.trader.game.loan.Loan
import dev.adriangrzebyk.trader.game.loan.LoanCollection
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import dev.adriangrzebyk.trader.game.loan.SeizedUnits
import dev.adriangrzebyk.trader.game.news.Expedition
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TipOff
import dev.adriangrzebyk.trader.game.news.TipOffNeed
import dev.adriangrzebyk.trader.game.sabotage.InvalidSabotageException
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget

/**
 * A player's membership in one game; [email] is the membership key. [money] is private to its owner (R29);
 * [marketPositions] are public (R28), and a player holds none until they earn one (R40, R43). [warehouses] and [factories]
 * are public too (R50), and nobody starts with any (R40, R50). [connections] are public (R81), and [connectionPoints] are the
 * starting points they still have to place (R82): 3 + d3 from the start of the game until they place them in turn 1's
 * Bidding, then 0. [reputation] is public too (R85). [loan] is their open loan, private like money (R29, R90), or null.
 * [agents] are their special operatives (R103): where they are is public, which they are is theirs alone (R105). [ledger] is
 * their own money history, private like money (R29, playtest 2026-11-20): every change to [money] goes through [pay] or
 * [receive], which record it. [expeditions] are their ships away on the scientific expedition (R114), public. [passengerGroups] are the passengers they took
 * this turn and owe delivery of (R125), private.
 */
data class Player(
	val email: String,
	val name: String,
	val corporationName: String,
	val money: Int,
	val ships: List<Ship>,
	val marketPositions: List<MarketPosition>,
	val warehouses: List<Warehouse>,
	val factories: List<Factory>,
	val connections: Connections,
	val connectionPoints: Int,
	val reputation: Reputation,
	val loan: Loan?,
	val agents: List<RecruitedAgent>,
	val ledger: MoneyLedger = MoneyLedger.START,
	val expeditions: List<Expedition> = emptyList(),
	val passengerGroups: List<PassengerGroup> = emptyList(),
	val eliminated: Boolean = false
) {

	init {
		require(warehouses.map { it.system }.toSet().size == warehouses.size) { "A player has at most one warehouse per system." }
	}

	/** This player after paying [amount] HT for [reason], recorded in their [ledger]. */
	fun pay(amount: Int, reason: String): Player = copy(money = money - amount, ledger = ledger.recorded(-amount, reason))

	/** This player after being paid [amount] HT for [reason], recorded in their [ledger]. */
	fun receive(amount: Int, reason: String): Player = copy(money = money + amount, ledger = ledger.recorded(amount, reason))

	/**
	 * This player after paying to learn a tip-off that needs [need] (R110): refused when their connection falls short or
	 * they can't pay [TIP_OFF_FEE].
	 */
	fun learnTipOff(need: TipOffNeed): Player {
		if (!need.isMetBy(connections)) {
			throw InvalidGameOperationException(need.refusal(connections))
		}
		if (money < TIP_OFF_FEE) {
			throw InvalidGameOperationException("Learning a tip-off costs $TIP_OFF_FEE HT; you have $money HT.")
		}
		return pay(TIP_OFF_FEE, "Learned a tip-off")
	}

	/** This player after paying [cost] HT for [reason], refused when they can't: an opportunity's price (R111). */
	private fun payFor(cost: Int, reason: String): Player {
		if (cost > money) {
			throw InvalidGameOperationException("This costs $cost HT; you have $money HT.")
		}
		return pay(cost, reason)
	}

	/** This player after buying [lot] at [price] HT a unit into their ship [shipName] (R112). */
	fun buySpecial(shipName: String?, lot: SpecialLot, price: Int): Player {
		val ship = ship(shipName) ?: throw InvalidGameOperationException("Choose one of your ships.")
		val loaded = ship.withSpecialLot(lot)
		checkPassengersKeepSeats(loaded)
		return payFor(lot.units * price, "Bought ${lot.units} ${lot.goods.displayName}").withShip(loaded)
	}

	/** The units of [goods] bound for [system] aboard this player's ships at [location] there: what a sale there sells (R112). */
	fun specialUnitsAt(goods: SpecialGoods, system: StarSystem, location: ShipLocation): Int =
		ships.filter { it.isAt(system, location) }.sumOf { it.specialUnits(goods, system) }

	/** This player after selling every unit of [goods] bound for [system] at [location] there for [price] HT each (R112). */
	fun sellSpecialAt(goods: SpecialGoods, system: StarSystem, location: ShipLocation, price: Int): Player {
		val units = specialUnitsAt(goods, system, location)
		if (units == 0) {
			throw InvalidGameOperationException("You have no ${goods.displayName} for ${system.displayName} here.")
		}
		val emptied = copy(ships = ships.map { if (it.isAt(system, location)) it.withoutSpecial(goods, system) else it })
		return emptied.receive(units * price, "Sold $units ${goods.displayName} at ${system.displayName}")
	}

	/** Whether Frank is on [system]'s Planet with this player (R108): +2 on their black-market roll there. */
	fun blackMarketBonusAt(system: StarSystem): Int =
		if (agent(Agent.FRANK)?.let { placeOf(it) == AgentPlace(system, ShipLocation.PLANET) } == true) Agent.FRANK_BLACK_MARKET_BONUS else 0

	/** This player after buying the military [ship] through an opportunity (R113): its hull, modules and crew. */
	fun buyMilitaryShip(ship: Ship): Player = copy(ships = ships + ship).payFor(ship.price(), "Bought ${ship.name}")

	/**
	 * This player after fitting military [modules] to their ship [shipName] on a Planet (R113), crew at its class, with
	 * [removed] modules taken off to make room ([Ship.withMilitaryModules]), each paying half its price back as in R65.
	 */
	fun fitMilitaryModules(shipName: String?, modules: List<Module>, removed: List<Module> = emptyList()): Player {
		val ship = ship(shipName) ?: throw InvalidGameOperationException("Choose one of your ships.")
		val fitted = ship.withMilitaryModules(modules, removed)
		checkPassengersKeepSeats(fitted)
		val refunded = removed.fold(this) { player, module ->
			player.receive(module.resaleValue(), "${module.displayName} module removed from ${ship.name}")
		}
		return refunded.payFor(modules.sumOf { it.fittingCost(ship.crewClass) }, "Military modules for ${ship.name}").withShip(fitted)
	}

	/**
	 * This player after boarding [group] (R125), paid its fare at once: the ship must be theirs, in the origin's Port, with a
	 * free seat for each group (not counting groups already booked on it).
	 */
	fun boardPassengers(group: PassengerGroup): Player {
		val ship = ship(group.ship) ?: throw InvalidGameOperationException("Choose one of your ships.")
		if (reputation.value < PassengerGroup.MIN_REPUTATION) {
			throw InvalidGameOperationException(
				"Passengers travel only with a corporation of reputation ${PassengerGroup.MIN_REPUTATION} or more; yours is ${reputation.value}."
			)
		}
		if (!ship.isInPortAt(group.from)) {
			throw InvalidGameOperationException("${ship.name} must be in ${group.from.displayName}'s Port to take passengers.")
		}
		if (group.groups > freeSeatsOn(ship)) {
			throw InvalidGameOperationException("${ship.name} has ${freeSeatsOn(ship)} free passenger seats.")
		}
		val booked =
			passengerGroups.firstOrNull { it.isSameTripAs(group) }?.let { passengerGroups - it + it.plus(group) } ?: (passengerGroups + group)
		return copy(passengerGroups = booked).receive(group.fare(), "Passengers from ${group.from.displayName} to ${group.to.displayName}")
	}

	/** The passenger seats of [ship] not yet booked this turn (R125). */
	fun freeSeatsOn(ship: Ship): Int = (ship.freePassengerSeats() - passengersBookedOn(ship.name)).coerceAtLeast(0)

	/** The passenger groups booked on the ship [shipName] this turn (R125). */
	private fun passengersBookedOn(shipName: String): Int = passengerGroups.filter { it.ship == shipName }.sumOf { it.groups }

	/**
	 * Refuses a change that leaves [changed] with fewer free passenger seats than are booked on it (R125): booked passengers are
	 * never evicted, so slaves, a delivery or taking a Passenger module off wait until they are delivered.
	 */
	private fun checkPassengersKeepSeats(changed: Ship) {
		if (changed.freePassengerSeats() < passengersBookedOn(changed.name)) {
			throw InvalidGameOperationException("${changed.name} has passengers booked: their seats can't be taken until they are delivered.")
		}
	}

	/** The public log line of [group] boarding (R125): "Orion Freight took 2 passenger groups from Beta Hydri to Tau Ceti." */
	fun boardedLog(group: PassengerGroup): String =
		"$corporationName took ${group.groups} passenger ${if (group.groups == 1) "group" else "groups"} from ${group.from.displayName} to ${group.to.displayName}."

	/** How many passenger groups this player took this turn on [route] (R125). */
	fun passengerGroupsOn(route: PassengerRoute): Int = passengerGroups.filter { it.route() == route }.sumOf { it.groups }

	/**
	 * This player once their passengers were settled as an Opportunity opens (R125). A group is delivered when its ship is in the
	 * destination's Port with seats left among its working Passenger modules; every other group, for whatever reason (the ship
	 * was lost, sold or damaged, the jump failed), costs its penalty, as far as the player's cash goes: the rest is forgiven. Nothing goes back to the pool. Each undelivered group
	 * gets a public line without the amount; delivered ones none.
	 */
	fun settlePassengers(): LoggedChange {
		val room = ships.associate { it.name to it.freePassengerSeats() }.toMutableMap()
		val undelivered = passengerGroups.map { group ->
			val delivered = ship(group.ship)?.passengersDeliveredTo(group.to, group.groups, room.getValue(group.ship)) ?: 0
			room[group.ship] = (room[group.ship] ?: 0) - delivered
			group.notDelivered(delivered)
		}.filter { it.groups > 0 }
		val settled = copy(passengerGroups = emptyList())
		// A player with no cash left is forgiven the rest (creator, 2026-11-23): money never goes below 0 through the penalty.
		val charge = minOf(undelivered.sumOf { it.penalty() }, money.coerceAtLeast(0))
		val charged = if (charge == 0) settled else settled.pay(charge, "Passengers not delivered")
		val lines = undelivered.map {
			"$corporationName's ${it.ship} didn't bring ${it.groups} passenger ${if (it.groups == 1) "group" else "groups"} to ${it.to.displayName}."
		}
		return LoggedChange(charged, lines)
	}

	/** This player with their ship [shipName] carrying the delivery of [tipOff] in a whole [space] module (R114); [minReputation] to accept. */
	fun takeDelivery(shipName: String?, tipOff: TipOff, space: Module, minReputation: Int): Player {
		val ship = ship(shipName) ?: throw InvalidGameOperationException("Choose one of your ships.")
		if (reputation.value < minReputation) {
			throw InvalidGameOperationException("This delivery needs reputation $minReputation; yours is ${reputation.value}.")
		}
		val carrying = ship.withDelivery(tipOff, space)
		checkPassengersKeepSeats(carrying)
		return withShip(carrying)
	}

	/** The public log line of the delivery aboard their ship [shipName] (R114): "Orion Freight delivered exotic plants to Epsilon Eridani". */
	fun deliveredLog(shipName: String): String {
		val terms = ship(shipName)?.deliveryTerms() ?: throw InvalidGameOperationException("$shipName carries no delivery.")
		return "$corporationName delivered ${terms.cargo} to ${terms.to.displayName}"
	}

	/**
	 * This player once their ship [shipName] delivered its job (R114, R121) in the destination Port: paid and its reputation
	 * changed. Refused when it carries none or isn't in that Port.
	 */
	fun deliver(shipName: String?): Player {
		val ship = ship(shipName) ?: throw InvalidGameOperationException("Choose one of your ships.")
		val terms = ship.deliveryTerms() ?: throw InvalidGameOperationException("${ship.name} carries no delivery.")
		if (!ship.canDeliver()) {
			throw InvalidGameOperationException("${ship.name} must be in ${terms.to.displayName}'s Port to deliver.")
		}
		return withShip(
			ship.delivered()
		).receive(terms.payment, "Delivered ${terms.cargo} to ${terms.to.displayName}").withReputationChanged(terms.reputation)
	}

	/** This player once their legal ship [shipName] left on the expedition, back in turn [returnTurn] (R114); its agents step off. */
	fun sendOnExpedition(shipName: String?, returnTurn: Int): Player {
		val ship =
			ship(shipName)?.takeUnless { it.isInShipyard() } ?: throw InvalidGameOperationException("Choose one of your ships out of the shipyard.")
		if (!ship.isLegal()) {
			throw InvalidGameOperationException("Only a ship with no military parts can go on the expedition.")
		}
		return copy(expeditions = expeditions + Expedition(ship, returnTurn)).withAgentsSteppedOff(ship).withoutShip(ship.name)
	}

	/** This player once their expedition ship [shipName] was lost on a 1 (R114). */
	fun abandonExpedition(shipName: String): Player = copy(expeditions = expeditions.filterNot { it.ship.name == shipName })

	/** This player's expedition ship [shipName] that's back in turn [turn], refused otherwise (R114). */
	fun expeditionDue(shipName: String?, turn: Int): Expedition = expeditions.firstOrNull { it.ship.name == shipName && it.isDueIn(turn) }
		?: throw InvalidGameOperationException("No expedition of yours is back now.")

	/** This player once [expedition] returned to [system] at [location] and paid [payment] HT (R114). */
	fun returnedFrom(expedition: Expedition, system: StarSystem, location: ShipLocation, payment: Int): Player =
		copy(expeditions = expeditions - expedition, ships = ships + expedition.ship.returnedTo(system, location))
			.receive(payment, "Scientific expedition")

	/** This player after the money fraud (E4): cash and any open loan halved, rounded down. */
	fun inflated(): Player = copy(loan = loan?.halved()).pay(money - money / 2, "Money fraud: cash halved")

	/** The special tax (E13): 1 HT per warehouse block, 2 HT per factory unit, 5 HT per ship. */
	fun specialTax(): Int = warehouses.sumOf { it.blocks } * 1 + factoryUnits() * 2 + ships.size * 5

	/** This player after the special tax (E13): paid when their cash covers it, waived otherwise. */
	fun afterSpecialTax(): Player = if (money >= specialTax()) pay(specialTax(), "Special tax") else this

	/**
	 * This player after the civil war at [system] (E5): their ships in its Port, their factories and warehouse there are
	 * confiscated with everything in them, and they're paid half their value: a ship's R66 price, 10 HT per block, and half
	 * of what building the factories would cost now at [prices]. Agents aboard step off into the Port (R107).
	 */
	fun afterCivilWar(system: StarSystem, prices: MarketPrices): Player {
		val ships = ships.filter { it.isInPortAt(system) }
		val warehouse = warehouseAt(system)
		val factories = factories.filter { it.system == system }
		val compensation = ships.sumOf { it.saleValue() } + (warehouse?.blocks ?: 0) * Warehouse.BLOCK_COST / 2 +
			factories.sumOf { (ProductionCost(prices.priceOf(MarketGood(system, it.goods))).of(it.size) / 2).toInt() }
		val stripped =
			copy(warehouses = warehouses.filterNot { it.system == system }, factories = this.factories.filterNot { it.system == system })
		return ships.fold(stripped) { player, ship -> player.confiscateShip(ship.name) }
			.receive(compensation, "Civil war at ${system.displayName}: half of what was confiscated")
	}

	/** Whether this player lost anything to the civil war at [system] (E5): a ship in its Port, a warehouse or a factory there. */
	fun hasAssetsAt(system: StarSystem): Boolean =
		ships.any { it.isInPortAt(system) } || warehouseAt(system) != null || factories.any { it.system == system }

	/**
	 * This player once pirates emptied their warehouse at [system] (E12), and the public line that tells it, as a
	 * sabotage's does (R101): how many units were lost, never which goods. An empty warehouse, or none, isn't logged.
	 */
	fun lootWarehouse(system: StarSystem): LoggedChange {
		val warehouse = warehouseAt(system)?.takeIf { it.unitsHeld() > 0 } ?: return LoggedChange(this, emptyList())
		val line = "Pirates at ${system.displayName}: $corporationName's warehouse lost ${warehouse.unitsHeld()} u"
		return LoggedChange(withWarehouse(warehouse.emptied()), listOf(line))
	}

	/** This player recording money movements in turn [number] from now on (R29's ledger). */
	fun inTurn(number: Int): Player = copy(ledger = ledger.inTurn(number))

	/** What this player's ships would fetch if all were sold now (R61's half price): the money sunk in them (creator, 2026-11-18). */
	fun shipsSaleValue(): Int = ships.sumOf { it.saleValue() }

	/**
	 * Every good in this player's ships and warehouses, one stock per good in [Goods] order with a lot per hold (ships first),
	 * valued at its mean price across the systems that trade it today ([prices]) (creator, 2026-11-18).
	 */
	fun goodsStocks(prices: MarketPrices): List<GoodsStock> = (ships + warehouses).flatMap { it.lots() }
		.groupBy { it.goods }.toSortedMap().map { (goods, lots) -> GoodsStock(goods, prices.averagePrice(goods), lots) }

	/** What the goods in this player's ships and warehouses are worth at each good's mean price today (creator, 2026-11-18). */
	fun goodsValue(prices: MarketPrices): Int = goodsStocks(prices).sumOf { it.value() }

	/** What one turn's output of all this player's factories is worth at each good's mean price today (creator, 2026-11-18). */
	fun factoryOutputValue(prices: MarketPrices): Int = factories.sumOf { it.outputValue(prices) }

	/** This player's ships in port at [system]: with their warehouse there, the only places they trade from (R23, R42, R49). */
	fun shipsInPortAt(system: StarSystem): List<Ship> = ships.filter { it.isInPortAt(system) }

	/** This player's warehouse at [system], or null when they have none there (R45). */
	fun warehouseAt(system: StarSystem): Warehouse? = warehouses.firstOrNull { it.system == system }

	/** How many units of [goods] this player holds at [system]: in their ships in port there and their warehouse there (R42, R49). */
	fun holdingAt(system: StarSystem, goods: Goods): Int =
		shipsInPortAt(system).sumOf { it.holding(goods) } + (warehouseAt(system)?.holding(goods) ?: 0)

	/**
	 * The most units this player can trade on [offer] when the counter allows [counterMax] (R34): a buy also capped by
	 * what they can afford at its price (R12), a sell by what their ships in port at that system and their warehouse
	 * there hold (R10, R42, R49). Never below 0.
	 */
	fun tradeMax(offer: Offer, counterMax: Int): Int {
		val cap = when (offer.type) {
			OfferType.BUY -> money / offer.price
			OfferType.SELL -> holdingAt(offer.system, offer.goods)
		}
		return minOf(counterMax, cap).coerceAtLeast(0)
	}

	/**
	 * This player after trading [amount] units on their [offer] (E05-S04), the counter allowing [counterMax]. [amount] is
	 * a whole number from 1 to [tradeMax] (S04-1). [loads] and [warehouseUnits] split it across their ships in port there
	 * and their warehouse there, adding up to it (R49); with neither a ship in port nor a warehouse there they're empty,
	 * and a buy's goods are all lost (S04-4, R23). A buy pays the offer's price per unit and loads what fits; a sell
	 * unloads and is paid (R34: at the offer's own price).
	 */
	fun trade(offer: Offer, counterMax: Int, amount: Int?, loads: List<ShipLoad>, warehouseUnits: Int = 0): Player {
		val max = tradeMax(offer, counterMax)
		if (max == 0) {
			throw InvalidTradeException(
				when (offer.type) {
					OfferType.BUY -> "You can't afford any units at ${offer.price} HT."
					OfferType.SELL -> "You have no ${offer.goods.name} in your ${holdingPlaces(
						offer.system
					)} at ${offer.system.displayName}, so you can't sell any."
				}
			)
		}
		if (amount == null || amount !in 1..max) {
			throw InvalidTradeException("Enter a whole number from 1 to $max.")
		}
		val here = shipsInPortAt(offer.system)
		if (loads.any { load -> here.none { it.name == load.shipName } }) {
			throw InvalidTradeException("Choose your ships in port at ${offer.system.displayName}.")
		}
		val warehouse = warehouseAt(offer.system)
		if (warehouseUnits > 0 && warehouse == null) {
			throw InvalidTradeException("You have no warehouse at ${offer.system.displayName}.")
		}
		if (loads.map { it.shipName }.toSet().size != loads.size || loads.any { it.units < 0 } || warehouseUnits < 0) {
			throw InvalidTradeException(
				"Enter a whole number of units, 0 or more, for each ${if (warehouse == null) "ship" else "ship and your warehouse"}."
			)
		}
		if ((here.isNotEmpty() || warehouse != null) && loads.sumOf { it.units } + warehouseUnits != amount) {
			throw InvalidTradeException("Your ${if (warehouse == null) "ships'" else "ships' and warehouse's"} units must add up to $amount.")
		}
		val moved = loads.fold(this) { player, load ->
			val ship = checkNotNull(player.ship(load.shipName))
			player.withShip(
				when (offer.type) {
					OfferType.BUY -> ship.load(offer.goods, load.units)
					OfferType.SELL -> ship.unload(offer.goods, load.units)
				}
			)
		}
		val stocked = if (warehouse == null) {
			moved
		} else {
			moved.withWarehouse(
				when (offer.type) {
					OfferType.BUY -> warehouse.load(offer.goods, warehouseUnits)
					OfferType.SELL -> warehouse.unload(offer.goods, warehouseUnits)
				}
			)
		}
		return when (offer.type) {
			OfferType.BUY -> stocked.pay(amount * offer.price, "Bought $amount ${offer.goods.name} at ${offer.system.displayName}")
			OfferType.SELL -> stocked.receive(amount * offer.price, "Sold $amount ${offer.goods.name} at ${offer.system.displayName}")
		}
	}

	/**
	 * This player after moving [units] of [goods] [from] one of their holders [to] another in the same port (R58, revised
	 * 2026-11-17): two of their ships in port at the same system, or a ship in port and their warehouse there, either way.
	 * Unlike a buy, a move never loses goods: it is refused when the source holds fewer than [units], or when the
	 * destination has too little free cargo space for them (POLY and ISO; CHIPS and FOOD always fit, R42).
	 */
	fun moveCargo(from: CargoHolder, to: CargoHolder, goods: Goods, units: Int): Player {
		val (source, destination) = cargoHolds(from, to)
		if (units < 1) {
			throw InvalidCargoMoveException("Enter a whole number of units, 1 or more.")
		}
		if (units > source.holding(goods)) {
			throw InvalidCargoMoveException("${source.cargoName()} has only ${source.holding(goods)} ${goods.name}.")
		}
		val room = destination.unitsThatFit(goods, units)
		if (room < units) {
			throw InvalidCargoMoveException("${destination.cargoName()} has room for only $room more ${goods.name}.")
		}
		return withCargoHold(source.unload(goods, units)).withCargoHold(destination.load(goods, units))
	}

	/**
	 * The ship or warehouse at each end of a move [from] [to] (R58): at least one end is one of this player's ships, every
	 * ship named is in port, two ships are different and in the same port, and a warehouse end is theirs in that port.
	 */
	private fun cargoHolds(from: CargoHolder, to: CargoHolder): Pair<CargoHold, CargoHold> {
		if (from is CargoHolder.Ship && from == to) {
			throw InvalidCargoMoveException("Choose two different ships.")
		}
		val (fromShip, toShip) = listOf(from, to).map { (it as? CargoHolder.Ship)?.let { named -> shipInPortForCargo(named.name) } }
		val port = (fromShip ?: toShip)?.system ?: throw InvalidCargoMoveException("Choose one of your ships.")
		if (fromShip != null && toShip != null && !toShip.isInPortAt(port)) {
			throw InvalidCargoMoveException("${fromShip.name} and ${toShip.name} aren't in the same port.")
		}
		val warehouse by lazy {
			warehouseAt(port) ?: throw InvalidCargoMoveException("You have no warehouse at ${port.displayName}.")
		}
		return (fromShip ?: warehouse) to (toShip ?: warehouse)
	}

	/** This player's ship [name], refused when it isn't theirs or isn't in port (R58). */
	private fun shipInPortForCargo(name: String?): Ship {
		val ship = ship(name) ?: throw InvalidCargoMoveException("Choose one of your ships.")
		if (!ship.isInPort()) {
			throw InvalidCargoMoveException("${ship.name} isn't in port. Goods move only between your ships and warehouse in the same port.")
		}
		return ship
	}

	/** This player's factory for [goods] at [system], if they have one (R46). */
	fun factoryAt(system: StarSystem, goods: Goods): Factory? = factories.firstOrNull { it.isFor(system, goods) }

	/** Whether this player has a factory, so they collect output on their Opportunity turn (R47, R102). */
	fun hasFactories(): Boolean = factories.isNotEmpty()

	/**
	 * This player after collecting this turn's output into [placements] (R47, E06-S03-5). Each factory makes its [Factory.size]
	 * units; a placement names one of this player's factories and a destination at its system, their warehouse there or
	 * one of their ships in port there, and a factory's placements can't add up to more than it makes. What fits is
	 * stored; POLY and ISO beyond the free cargo space are lost (R16), and so is whatever isn't placed, which includes
	 * CHIPS and FOOD with nowhere to keep them (R47).
	 */
	fun collect(placements: List<OutputPlacement>): Player {
		if (placements.any { it.units < 0 }) {
			throw InvalidCollectException("Enter a whole number of units, 0 or more, for each destination.")
		}
		placements.groupBy { it.system to it.goods }.forEach { (key, group) ->
			val factory = factoryAt(key.first, key.second)
				?: throw InvalidCollectException("You have no ${key.second.name} factory at ${key.first.displayName}.")
			if (group.sumOf { it.units } > factory.output()) {
				throw InvalidCollectException("Your ${factory.goods.name} factory at ${factory.system.displayName} makes ${factory.output()} units.")
			}
		}
		val stored = placements.fold(this) { player, placement -> player.stored(placement) }
		return stored.withFactoriesCollected()
	}

	/**
	 * The public log lines of the collect that turned [before] into this player (R50, E06-S07-1), one per factory: what
	 * was stored is what they now hold more of at the factory's system, and the rest of its output was lost.
	 */
	fun collectLog(before: Player): List<String> = before.factories.map { factory ->
		factory.collectLog(corporationName, holdingAt(factory.system, factory.goods) - before.holdingAt(factory.system, factory.goods))
	}

	/** This player once a Collect went by: each disabled factory has one turn fewer to go (R99). */
	private fun withFactoriesCollected(): Player = copy(factories = factories.map { it.afterCollect() })

	/** This player without their ship [shipName]. */
	private fun withoutShip(shipName: String): Player = copy(ships = ships.filterNot { it.name == shipName })

	/** This player with [changed] as their loan, or none. */
	private fun withLoan(changed: Loan?): Player = copy(loan = changed)

	/** This player with [recruited] among their agents. */
	private fun withAgentAdded(recruited: RecruitedAgent): Player = copy(agents = agents + recruited)

	/** This player without their [agent]. */
	private fun withoutAgent(agent: Agent): Player = copy(agents = agents.filterNot { it.agent == agent })

	/** This player after one [placement] of their output: into the named ship in port there, or their warehouse at that system. */
	private fun stored(placement: OutputPlacement): Player {
		if (placement.shipName == null) {
			val warehouse = warehouseAt(placement.system)
				?: throw InvalidCollectException("You have no warehouse at ${placement.system.displayName}.")
			return withWarehouse(warehouse.load(placement.goods, placement.units))
		}
		val ship = shipsInPortAt(placement.system).firstOrNull { it.name == placement.shipName }
			?: throw InvalidCollectException("Choose your ships in port at ${placement.system.displayName}.")
		return withShip(ship.load(placement.goods, placement.units))
	}

	/**
	 * This player after buying [blocks] warehouse blocks at [system] (R45, E06-S04): added to their warehouse there, or
	 * creating it, for [Warehouse.BLOCK_COST] HT each. Needs enough money; no ship (creator, 2026-11-16).
	 */
	fun buyWarehouseBlocks(system: StarSystem, blocks: Int): Player {
		if (blocks < 1) {
			throw InvalidInvestmentException("Enter a whole number of blocks, 1 or more.")
		}
		val cost = blocks.toLong() * Warehouse.BLOCK_COST
		if (cost > money) {
			throw InvalidInvestmentException("$blocks ${if (blocks == 1) "block costs" else "blocks cost"} $cost HT; you have $money HT.")
		}
		val warehouse = warehouseAt(system)?.addBlocks(blocks) ?: Warehouse(system, blocks, emptyMap())
		return pay(cost.toInt(), "Warehouse at ${system.displayName}: $blocks ${if (blocks == 1) "block" else "blocks"}").withWarehouse(warehouse)
	}

	/**
	 * This player after buying [units] of production of [goods] at [system] (R46, E06-S05): added to their factory there,
	 * or creating it, for [units] × (5 × the good's price in [prices] + 10) HT ([ProductionCost]). The system must be able to produce the
	 * good ([StarSystem.producibleGoods]) and they need enough money; no ship and no factory there already (creator, 2026-11-16).
	 * Their factory units in total, across all systems and goods, can't go above their political + business connections
	 * (R84, E12-S03); factories they own already are never taken away.
	 */
	fun buyFactoryUnits(system: StarSystem, goods: Goods, units: Int, prices: MarketPrices): Player {
		if (units < 1) {
			throw InvalidInvestmentException("Enter a whole number of units, 1 or more.")
		}
		if (goods !in system.producibleGoods) {
			throw InvalidInvestmentException("${system.displayName} can't produce ${goods.name}.")
		}
		if (units > factoryCap() - factoryUnits()) {
			throw InvalidInvestmentException(connections.factoryCapText())
		}
		val cost = ProductionCost(prices.priceOf(MarketGood(system, goods))).of(units)
		if (cost > money) {
			throw InvalidInvestmentException("$units ${if (units == 1) "unit costs" else "units cost"} $cost HT; you have $money HT.")
		}
		val factory = factoryAt(system, goods)?.addUnits(units) ?: Factory.built(system, goods, units)
		return pay(
			cost.toInt(),
			"${goods.name} factory at ${system.displayName}: $units ${if (units == 1) "unit" else "units"}"
		).withFactory(factory)
	}

	/** The units of factory this player owns in total, across all systems and goods: what the cap counts (R84). */
	fun factoryUnits(): Int = factories.sumOf { it.size }

	/** The most units of factory this player may own in total (R84): their political + business connections. */
	fun factoryCap(): Int = connections.factoryCap()

	/** What raising [kind] costs this player now (R83), or null when it's at 10. */
	fun connectionRaiseCost(kind: ConnectionKind): Int? = connections.raiseCost(kind)

	/**
	 * This player after placing their starting [connectionPoints] as [political], [business] and [criminal] (R82,
	 * E12-S01): once, final, and only while they have points to place. A null is a number that was missing or not whole.
	 */
	fun placeConnections(political: Int?, business: Int?, criminal: Int?): Player {
		if (connectionPoints == 0) {
			throw InvalidGameOperationException("You have already placed your connection points.")
		}
		return copy(connections = Connections.placed(political, business, criminal, connectionPoints), connectionPoints = 0)
	}

	/** Refuses this player's done signal in turn 1 while they have no ship yet: they choose its name and Port (R132). */
	fun requireStartingShip() {
		if (ships.isEmpty()) {
			throw InvalidGameOperationException("Choose your ship's name and Port first.")
		}
	}

	/** This player with [ship] as their starting ship (R132): refused when they already have a ship. */
	fun withStartingShip(ship: Ship): Player {
		if (ships.isNotEmpty()) {
			throw InvalidGameOperationException("You've already placed your ship.")
		}
		return copy(ships = listOf(ship))
	}

	/** Refuses this player's done signal while they still have starting points to place (R82, E12-S01-3). */
	fun requireConnectionsPlaced() {
		if (connectionPoints > 0) {
			throw InvalidGameOperationException("Place your $connectionPoints connection points first.")
		}
	}

	/**
	 * This player after raising their [kind] connections by one point in Investment (R83, E12-S02), paying the new level
	 * × 10 HT ([Connections.raiseCost]). Refused at 10, or when they can't pay. Once per Investment is the turn's to check.
	 */
	fun raiseConnection(kind: ConnectionKind): Player {
		val cost = connections.raiseCost(kind)
			?: throw InvalidConnectionsException("Your ${kind.displayName} connections are already at ${Connections.MAX}.")
		if (cost > money) {
			throw InvalidConnectionsException(
				"Raising ${kind.displayName} connections to ${connections.of(kind) + 1} costs $cost HT; you have $money HT."
			)
		}
		return copy(
			connections = connections.raised(kind),
			reputation = reputation.changedBy(connectionReputationChange(kind))
		).pay(cost, "${kind.displayName.replaceFirstChar { it.uppercase() }} connections raised to ${connections.of(kind) + 1}")
	}

	/** What raising [kind] does to this player's reputation (R86): its change, a loss made smaller by JB (R108). */
	fun connectionReputationChange(kind: ConnectionKind): Int =
		if (kind.reputationChange < 0) -reputationLossAfterAgents(-kind.reputationChange) else kind.reputationChange

	/**
	 * The public log line of a change of [delta] to this player's reputation, given after it (R86, E13-S01-4): "Vega Lines's
	 * reputation: −7 for trying to intercept (rolled 2d6: 7) → 13". [delta] is the change the rule made, so a change
	 * clamped to nothing still shows why. When JB made a loss [jbSaved] smaller (R108), the sum is shown in the same line,
	 * as Wolf's is in the initiative line (R105): "Kessler Cargo's reputation: −6 + JB 1 = −5 for sabotage … → 15".
	 */
	fun reputationLog(delta: Int, reason: String, jbSaved: Int = 0): String {
		val change = if (jbSaved == 0) signed(delta) else "${signed(delta - jbSaved)} + ${Agent.JB.displayName} $jbSaved = ${signed(delta)}"
		return "$corporationName's reputation: $change for $reason \u2192 ${reputation.value}"
	}

	private fun signed(delta: Int): String = if (delta >= 0) "+$delta" else "\u2212${-delta}"

	/** This player after their reputation moved by [delta], clamped to 0–40 (R86). */
	fun withReputationChanged(delta: Int): Player = copy(reputation = reputation.changedBy(delta))

	/** What a reputation loss of [loss] really costs this player (R108): 1 less, never below 0, while JB is in any Port. */
	fun reputationLossAfterAgents(loss: Int): Int =
		if (loss > 0 && isAgentInPort(Agent.JB)) (loss - Agent.JB_REPUTATION_REDUCTION).coerceAtLeast(0) else loss

	/**
	 * This player after losing [loss] reputation for [reason] (R86), and the public line that tells it, with JB's part in
	 * it when he made it smaller (R105, R108: "Kessler Cargo's reputation: −6 + JB 1 = −5 for sabotage … → 15"). A loss
	 * of 0 isn't logged.
	 */
	fun loseReputation(loss: Int, reason: String): LoggedChange {
		val actual = reputationLossAfterAgents(loss)
		val changed = withReputationChanged(-actual)
		return LoggedChange(changed, listOfNotNull(changed.reputationLog(-actual, reason, loss - actual).takeIf { loss > 0 }))
	}

	/**
	 * This player after buying reputation in Investment (R87, E13-S03): [Reputation.BUY_COST] HT for [roll] (a d6) more.
	 * Refused at 40 or with too little money; once per Investment is the turn's to check.
	 */
	fun buyReputation(roll: Int): Player {
		if (reputation.isMax()) {
			throw InvalidInvestmentException("Your reputation is already at ${Reputation.MAX}.")
		}
		if (money < Reputation.BUY_COST) {
			throw InvalidInvestmentException("Buying reputation costs ${Reputation.BUY_COST} HT; you have $money HT.")
		}
		return copy(reputation = reputation.changedBy(roll)).pay(Reputation.BUY_COST, "Bought reputation")
	}

	/** What the end of a turn adds to this player's reputation (R86): 3 between 1 and 17, else 0. */
	fun reputationRecovery(): Int = reputation.recovery()

	/** The money bonus this player's reputation pays at the end of a turn (R88), 0 below 25. */
	fun reputationBonus(): Int = reputation.bonus()

	/** The public log line of this player's raise of [kind], at its new level (R83, E12-S02-5); the cost isn't said (R29). */
	fun connectionRaisedLog(kind: ConnectionKind): String =
		"$corporationName raised ${kind.displayName} connections to ${connections.of(kind)}"

	/** The public log line of this player's starting placement (R82, E12-S01-2). */
	fun connectionsPlacedLog(): String =
		"$corporationName's connections: political ${connections.political}, business ${connections.business}, criminal ${connections.criminal}"

	/** The public log line of this player's [shipName] having jumped (S02-7), with the 2d6 [roll] when one was made (R68). */
	fun jumpedLog(shipName: String, roll: Int?): String =
		"Jump: $corporationName — ${checkNotNull(ship(shipName)).jumpLog()}" + (roll?.let { " (rolled $it)" } ?: "")

	/** The public log line of this player's [shipName] failing its jump to [destination] on [roll] (R68, S02-7). */
	fun failedJumpLog(shipName: String, destination: StarSystem, roll: Int): String =
		"Jump: $corporationName — ${checkNotNull(ship(shipName)).failedJumpLog(destination, roll)}"

	/** The public log line of this player's [shipName] landing where it is now (S03-3, R77). */
	/** The public log line of [shipName] entering or leaving its Port's Parking Hangar (R127); the fee isn't in it (R29). */
	fun hangarLog(shipName: String, parked: Boolean): String {
		val ship = checkNotNull(ship(shipName))
		return "Parking: $corporationName — $shipName ${if (parked) "is in" else "leaves"} the Parking Hangar (${ship.system.displayName})"
	}

	fun landedLog(shipName: String): String = "Landing: $corporationName — ${checkNotNull(ship(shipName)).landingLog()}"

	/** The public log line of [blocks] warehouse blocks bought at [system] (R50: without the price). */
	fun warehouseBlocksLog(system: StarSystem, blocks: Int): String =
		"$corporationName added $blocks warehouse ${if (blocks == 1) "block" else "blocks"} at ${system.displayName}"

	/** The public log line of [units] of [goods] production bought at [system] (R50: without the price). */
	fun factoryUnitsLog(system: StarSystem, goods: Goods, units: Int): String =
		"$corporationName added ${if (units == 1) "1 unit" else "$units units"} of ${goods.name} production at ${system.displayName}"

	/** The public log line of a ship [shipName] ordered at [shipyard] (R50, R57: never its hull or price). */
	fun shipOrderedLog(shipName: String, shipyard: StarSystem): String =
		"$corporationName ordered $shipName at the ${shipyard.displayName} shipyard"

	/** The public log line of [shipName] sold (R66: without the amount, R29). */
	fun shipSoldLog(shipName: String): String = "$corporationName sold $shipName"

	/** The public log line of this player's starting roll of [roll] on the d3 (R82, E12-S01-1). */
	fun connectionPointsRolledLog(roll: Int): String = "$corporationName rolled $roll: $connectionPoints connection points to place"

	/**
	 * The level a rise in market position takes this player's business connections to (R83, E12-S02-6), or null when
	 * they're at 10 already and it adds nothing.
	 */
	fun businessAfterMarketRise(): Int? = connections.afterMarketRise().business.takeIf { it != connections.business }

	/**
	 * This player after buying [ship] (R61), fresh from [Ship.commission]: it costs [Ship.price], which they must be able to
	 * pay in full. No ship of theirs needs to be at the yard: in Investment a player is everywhere.
	 */
	fun buyShip(ship: Ship): Player {
		val cost = ship.price()
		if (cost > money) {
			throw InvalidInvestmentException("This ship costs $cost HT; you have $money HT.")
		}
		return copy(ships = ships + ship).pay(cost, "Bought ${ship.name}")
	}

	/**
	 * This player after fitting one more [module] to their ship [shipName] at a shipyard (R65), paying its price and its
	 * crew at the ship's class ([Module.fittingCost]).
	 */
	fun addModule(shipName: String, module: Module): Player {
		val ship = shipToRefit(shipName)
		val refitted = ship.withModuleAdded(module)
		val cost = module.fittingCost(ship.crewClass)
		if (cost > money) {
			throw InvalidInvestmentException("A ${module.displayName} module costs $cost HT; you have $money HT.")
		}
		return pay(cost, "${module.displayName} module for ${ship.name}").withShip(refitted)
	}

	/** This player after removing one [module] from their ship [shipName] in port or in the yard (R65), paid half its price back. */
	fun removeModule(shipName: String, module: Module): Player {
		val ship = shipToRefit(shipName)
		val removed = ship.withModuleRemoved(module)
		checkPassengersKeepSeats(removed)
		return receive(module.resaleValue(), "${module.displayName} module removed from ${ship.name}").withShip(removed)
	}

	/**
	 * This player after replacing the whole crew of their ship [shipName] with one of [crewClass] (R89), in port or in the
	 * yard like a refit (R65), paying the new crew's full price ([Ship.crewCost]).
	 */
	fun replaceCrew(shipName: String, crewClass: CrewClass): Player {
		val ship = shipToRefit(shipName)
		val replaced = ship.withCrew(crewClass)
		val cost = ship.crewCost(crewClass)
		if (cost > money) {
			throw InvalidInvestmentException("A class ${crewClass.name} crew for ${ship.name} costs $cost HT; you have $money HT.")
		}
		return pay(cost, "Class ${crewClass.name} crew for ${ship.name}").withShip(replaced)
	}

	/**
	 * This player after taking one legal [module] off their ship [shipName] in Port and storing it in their warehouse there
	 * (R122): free, nothing paid back.
	 */
	fun storeModule(shipName: String, module: Module): Player {
		val ship = shipInPort(shipName)
		val warehouse = warehouseAt(ship.system) ?: throw InvalidInvestmentException("You have no warehouse at ${ship.system.displayName}.")
		val off = ship.withModuleTakenOff(module)
		checkPassengersKeepSeats(off)
		return withWarehouse(warehouse.withModuleStored(module)).withShip(off)
	}

	/** This player after fitting one [module] stored in their warehouse to their ship [shipName] in that Port (R122): free. */
	fun fitStoredModule(shipName: String, module: Module): Player {
		val ship = shipInPort(shipName)
		val warehouse = warehouseAt(ship.system) ?: throw InvalidInvestmentException("You have no warehouse at ${ship.system.displayName}.")
		return withWarehouse(warehouse.withModuleTaken(module)).withShip(ship.withModuleFitted(module))
	}

	/**
	 * This player after moving one military [module] from their ship [fromShip] to their ship [toShip], both in Port at a
	 * system that admits military ships (R123): free, the crew coming along. A legal module goes through a warehouse (R122).
	 */
	fun moveMilitaryModule(fromShip: String, toShip: String?, module: Module): Player {
		val from = shipInPort(fromShip)
		val to = shipInPort(toShip ?: throw InvalidInvestmentException("Choose the ship it goes to."))
		when {
			from.name == to.name -> throw InvalidInvestmentException("Choose two different ships.")
			from.system != to.system -> throw InvalidInvestmentException("${from.name} and ${to.name} aren't in the same port.")
			!module.military -> throw InvalidInvestmentException("${module.displayName} is legal: move it through your warehouse.")
			!from.system.locations.admitsMilitaryShips() -> throw InvalidInvestmentException(
				"Military modules change ships only in a Port with legal system 1 or 2; ${from.system.displayName}'s is " +
					"${from.system.locations.legalSystem}."
			)
		}
		val off = from.withModuleTakenOff(module)
		checkPassengersKeepSeats(off)
		return withShip(off).withShip(to.withModuleFitted(module))
	}

	/** What their ship [shipName] can do with its modules where it is now (R122, R123); none out of port. */
	fun moduleMoves(shipName: String): ModuleMoves {
		val ship = ship(shipName)?.takeIf { it.isInPort() } ?: return ModuleMoves.NONE
		val warehouse = warehouseAt(ship.system)
		val canComeOff = ship.modules.distinct().filter { ship.canTakeOff(it) && ship.freeSeatsWithout(it) >= passengersBookedOn(ship.name) }
		val storable = if (warehouse?.hasRoomForModule() == true) canComeOff.filterNot { it.military } else emptyList()
		val stored = warehouse?.modules.orEmpty().distinct().filter { ship.hasRoomFor(it) }
		val others = if (ship.system.locations.admitsMilitaryShips()) {
			ships.filter {
				it.name != ship.name && it.isInPortAt(ship.system)
			}
		} else {
			emptyList()
		}
		val transfers = others.flatMap { other ->
			canComeOff.filter { it.military && other.hasRoomFor(it) }.map { ModuleTransfer(it, other.name) }
		}
		return ModuleMoves(storable, stored, transfers)
	}

	/** Their ship [shipName], once checked that it's theirs and in Port, where modules move (R122, R123). */
	private fun shipInPort(shipName: String): Ship {
		val ship = ship(shipName) ?: throw InvalidInvestmentException("Choose one of your ships.")
		if (!ship.isInPort()) {
			throw InvalidInvestmentException("${ship.name} must be in port to move modules.")
		}
		return ship
	}

	/** Their ship [shipName], once checked that it's theirs and in port or in the yard, where refits happen (R65). */
	private fun shipToRefit(shipName: String): Ship {
		val ship = ship(shipName) ?: throw InvalidInvestmentException("Choose one of your ships.")
		if (!ship.canBeRefitted()) {
			throw InvalidInvestmentException("${ship.name} must be in port to refit.")
		}
		return ship
	}

	/**
	 * This player after selling their ship [shipName] (R66), wherever it is, for [Ship.saleValue]. Its cargo is never in
	 * the way: whatever fits goes into their warehouse at the ship's system, if they have one there ([Warehouse.storeCargoOf]);
	 * the rest is lost.
	 */
	fun sellShip(shipName: String): Player {
		val ship = ship(shipName) ?: throw InvalidInvestmentException("Choose one of your ships.")
		val warehouse = warehouseAt(ship.system)
		val sold = withAgentsSteppedOff(ship).withoutShip(ship.name).receive(ship.saleValue(), "Sold ${ship.name}")
		return if (warehouse == null) sold else sold.withWarehouse(warehouse.storeCargoOf(ship))
	}

	/**
	 * This player after repairing all the damage on their ship [shipName] at a shipyard (R79), paying [Ship.repairCost];
	 * refused when it has none or they can't pay.
	 */
	fun repairShip(shipName: String): Player {
		val ship = ship(shipName) ?: throw InvalidInvestmentException("Choose one of your ships.")
		if (!ship.canBeRepaired()) {
			throw InvalidInvestmentException(
				"${ship.name} must be at a shipyard to repair: in port at Beta Hydri, Epsilon Eridani or Tau Ceti."
			)
		}
		val repaired = ship.repaired()
		val cost = ship.repairCost()
		if (cost > money) {
			throw InvalidInvestmentException("Repairing ${ship.name} costs $cost HT; you have $money HT.")
		}
		return pay(cost, "Repaired ${ship.name}").withShip(repaired)
	}

	/** This player's ships in Space at [system] (R70, R73): those that can intercept there and fight. */
	fun shipsInSpaceAt(system: StarSystem): List<Ship> = ships.filter { it.isInSpaceAt(system) }

	/**
	 * This player after their ship [shipName] took [hits] placed as [placement] in a battle (R76); a ship the hits destroy
	 * is gone with everything aboard (R77).
	 */
	fun takeHits(shipName: String, hits: Int, placement: HitPlacement): Player {
		val hit = checkNotNull(ship(shipName)).takeHits(hits, placement)
		return if (hit.isDestroyed()) loseShip(shipName) else withShip(hit)
	}

	/**
	 * This player without their ship [shipName], destroyed in a battle in Space with everything aboard (R77), its agents
	 * included: they're killed and never come back (R107).
	 */
	fun loseShip(shipName: String): Player =
		copy(ships = ships.filterNot { it.name == shipName }, agents = agents.filterNot { it.isAboard(shipName) })

	/** How many of this player's agents are aboard [shipName]: public, unlike who they are (R105). */
	fun agentsAboard(shipName: String): Int = agents.count { it.isAboard(shipName) }

	/** The public log line of the agents killed aboard [shipName] (R107), or null with none: "2 agents aboard Starfarer were killed". */
	fun agentsKilledLog(shipName: String): String? = agentsAboard(shipName).takeIf { it > 0 }?.let {
		if (it == 1) "1 agent aboard $shipName was killed" else "$it agents aboard $shipName were killed"
	}

	/** This player's ships that can jump (R64): every one but those still in the shipyard. */
	fun jumpableShips(): List<Ship> = ships.filterNot { it.isInShipyard() }

	/** Whether this player has the money for a place in a Parking Hangar (R127). */
	fun canAffordHangar(): Boolean = money >= Ship.HANGAR_FEE

	/**
	 * The names of this player's ships whose hangar place was paid for before turn [turn] and are still in it (R127): the
	 * ones asked whether they stay when the player ends their jump slot.
	 */
	fun hangarRenewalsDue(turn: Int): List<String> = ships.filter { it.isDueHangarRenewal(turn) }.map { it.name }

	/** This player after their ship [shipName] was parked for turn [turn], paying the fee (R127); refused without the money. */
	fun parkShip(shipName: String, turn: Int): Player {
		val ship = checkNotNull(ship(shipName))
		if (!canAffordHangar()) {
			throw InvalidHangarException("A place in the Parking Hangar costs ${Ship.HANGAR_FEE} HT; you have $money HT.")
		}
		return withShip(ship.parkedOn(turn)).pay(Ship.HANGAR_FEE, "Parking Hangar at ${ship.system.displayName} for $shipName")
	}

	/** This player after their ship [shipName] left its hangar (R127). */
	fun leaveHangar(shipName: String): Player = withShip(checkNotNull(ship(shipName)).leftHangar())

	/** This player's ships that leave the yard in turn [turn]'s Investment (R63). */
	fun shipsLeavingYardIn(turn: Int): List<Ship> = ships.filter { it.leavesYardIn(turn) }

	/** This player once their ships bought before turn [turn] left the yard (R63), each into the Port of its system. */
	fun releaseShipsFromYard(turn: Int): Player = copy(ships = ships.map { if (it.leavesYardIn(turn)) it.leaveYard() else it })

	/**
	 * This player after selling [units] units of their [goods] factory at [system] (R96), at [Factory.salePricePerUnit]
	 * each; a factory left with no units is gone, and may be built again later (R46).
	 */
	fun sellFactoryUnits(system: StarSystem, goods: Goods, units: Int, prices: MarketPrices): Player {
		val factory = factoryAt(system, goods) ?: throw InvalidInvestmentException("You have no ${goods.name} factory at ${system.displayName}.")
		val left = factory.withoutUnits(units)
		return receive(
			units * factory.salePricePerUnit(prices),
			"Sold $units ${goods.name} factory ${if (units == 1) "unit" else "units"} at ${system.displayName}"
		)
			.withFactoryReplaced(factory, left)
	}

	/**
	 * This player once a sabotage's [effect] hit their asset [target] (R99), and what it came to. Nothing happens at 0. A
	 * factory is disabled for [effect] more Collects. A warehouse holding no more than [effect] units loses them all; one
	 * holding more waits for the saboteur to choose which, and an empty one loses nothing and isn't logged. A ship that can't
	 * survive [effect] hits is destroyed at once with its cargo, its agents stepping off (R107); otherwise its hits wait for
	 * this player to place them.
	 */
	fun sufferSabotage(target: SabotageTarget, effect: Int): SabotageOutcome {
		if (effect == 0) {
			return SabotageOutcome(this, emptyMap(), null, false)
		}
		return when (target) {
			is SabotageTarget.Factory -> SabotageOutcome(withFactoryDisabled(target.system, target.goods, effect), emptyMap(), effect, false)
			is SabotageTarget.Warehouse -> {
				val held = checkNotNull(warehouseAt(target.system)).cargo.filterValues { it > 0 }
				val total = held.values.sum()
				when {
					total == 0 -> SabotageOutcome(this, emptyMap(), null, false)
					total <= effect -> SabotageOutcome(withWarehouseLoss(target.system, held), held, total, false)
					else -> SabotageOutcome(this, null, effect, false)
				}
			}
			is SabotageTarget.Ship -> {
				val ship = checkNotNull(ship(target.name))
				if (effect > ship.hitsSurvivable()) {
					SabotageOutcome(destroyedBySabotage(ship.name), ship.cargo, effect, true)
				} else {
					SabotageOutcome(this, null, effect, false)
				}
			}
		}
	}

	/** This player once a sabotage disabled their [goods] factory at [system] for [turns] more Collects (R99). */
	private fun withFactoryDisabled(system: StarSystem, goods: Goods, turns: Int): Player {
		val factory = checkNotNull(factoryAt(system, goods))
		return withFactoryReplaced(factory, factory.disabledFor(turns))
	}

	private fun withFactoryReplaced(factory: Factory, by: Factory?): Player =
		copy(factories = factories.mapNotNull { if (it.isFor(factory.system, factory.goods)) by else it })

	// Loans (R90–R96, D22).

	/** The most this player may borrow now (R90): 100 HT a ship they own, yard and damaged ones included, 20 a warehouse block, plus their cash. */
	fun borrowingCap(): Int = LOAN_PER_SHIP * ships.size + LOAN_PER_BLOCK * warehouses.sumOf { it.blocks } + money

	/**
	 * This player after borrowing [amount] HT over [term] in turn [turn] (R90, R91): paid in at once. Refused while a loan
	 * is open, for a missing term, and for an amount that isn't from 1 up to [borrowingCap].
	 */
	fun takeLoan(amount: Int?, term: LoanTerm?, turn: Int): Player {
		if (loan != null) {
			throw InvalidInvestmentException("You already have a loan; repay it before taking another.")
		}
		if (term == null) {
			throw InvalidInvestmentException("Choose a term of 4 or 8 turns.")
		}
		val cap = borrowingCap()
		if (amount == null || amount < 1) {
			throw InvalidInvestmentException("Enter a whole number of HT, 1 or more.")
		}
		if (amount > cap) {
			throw InvalidInvestmentException("You can borrow at most $cap HT.")
		}
		return copy(loan = Loan.take(amount, term, reputation.value, turn)).receive(amount, "Loan taken")
	}

	/** This player's open loan, refused when they have none. */
	private fun openLoan(): Loan = loan ?: throw InvalidInvestmentException("You have no loan.")

	/** Whether this player owes interest in turn [turn]'s Investment (R92). Paying it is the turn's to record. */
	fun owesInterestIn(turn: Int): Boolean = loan?.isInterestDue(turn) == true

	/** This player after paying their loan's interest for turn [turn] (R92), refused when none is due or they can't afford it. */
	fun payInterest(turn: Int): Player {
		val open = openLoan()
		if (!open.isInterestDue(turn)) {
			throw InvalidInvestmentException("No interest is due this turn.")
		}
		if (open.interest > money) {
			throw InvalidInvestmentException("Interest is ${open.interest} HT; you have $money HT.")
		}
		return pay(open.interest, "Loan interest")
	}

	/**
	 * This player after repaying their whole loan (R93), plus this turn's interest when [interestUnpaid]. Refused when they
	 * can't afford it all: there's no partial repayment.
	 */
	fun repayLoan(interestUnpaid: Boolean): Player {
		val cost = openLoan().repayment(interestUnpaid)
		if (cost > money) {
			throw InvalidInvestmentException("Repaying costs $cost HT; you have $money HT.")
		}
		return copy(loan = null).pay(cost, "Loan repaid")
	}

	/**
	 * This player once their interest for a turn went unpaid (R92): −4 reputation (JB may make it 3, R108), logged without
	 * the amount, and the interest added to what they owe.
	 */
	fun withUnpaidInterest(): LoggedChange {
		val open = openLoan()
		val lost = withLoan(open.withInterestAdded()).loseReputation(UNPAID_INTEREST_REPUTATION, "unpaid loan interest")
		return lost
	}

	/**
	 * This player once their loan fell due at the end of its term (R94): what they owe comes from cash first, then from their
	 * ships sold at [Ship.saleValue], highest first, then from factory units at [Factory.salePricePerUnit] ([prices]),
	 * highest first and only as many as needed. A surplus stays in cash; whatever is still owed is written off.
	 */
	fun collectLoan(prices: MarketPrices): Pair<Player, LoanCollection> =
		withLoan(null).payOffBySelling(openLoan().amount, prices, "Loan collected")

	/**
	 * This player after paying [amount] HT for [reason] from cash first, then from their ships sold at [Ship.saleValue],
	 * highest first, then from factory units at [Factory.salePricePerUnit] ([prices]), highest first and only as many as
	 * needed (R94, R129). A surplus stays in cash; whatever is still owed is written off.
	 */
	private fun payOffBySelling(amount: Int, prices: MarketPrices, reason: String): Pair<Player, LoanCollection> {
		var owed = amount
		var player = this
		var collected = 0
		fun settle() {
			val paid = minOf(player.money, owed)
			player = player.pay(paid, reason)
			owed -= paid
			collected += paid
		}
		settle()
		val seizedShips = mutableListOf<String>()
		for (ship in ships.sortedByDescending { it.saleValue() }) {
			if (owed == 0) break
			player = player.sellShip(ship.name)
			seizedShips += ship.name
			settle()
		}
		val seizedUnits = mutableListOf<SeizedUnits>()
		for (factory in factories.sortedByDescending { it.salePricePerUnit(prices) }) {
			if (owed == 0) break
			val price = factory.salePricePerUnit(prices)
			val units = minOf(factory.size, (owed + price - 1) / price)
			player = player.sellFactoryUnits(factory.system, factory.goods, units, prices)
			seizedUnits += SeizedUnits(factory.system, factory.goods, units)
			settle()
		}
		return player to LoanCollection(collected, seizedShips, seizedUnits, owed)
	}

	/** The ships of this player in Space, anywhere: the ones police controls (R128). */
	fun shipsInSpace(): List<Ship> = ships.filter { it.isInSpace() }

	/** What [shipName] adds to its roll when police controls Space (R128): a working Battle comm module's +2. */
	fun policeBonusFor(shipName: String): Int = checkNotNull(ship(shipName)).policeBonus()

	/**
	 * This player after a reputation control read [result] (R129): the fine paid, from assets sold when cash falls short
	 * (what that took is the second part, null without a fine), every connection lowered and the reputation set, as the creator's
	 * table says. An eliminated result is [eliminate]; nothing else is paid then.
	 */
	fun afterControl(result: ControlResult, prices: MarketPrices): Pair<Player, LoanCollection?> {
		if (result.eliminated) {
			return eliminate() to null
		}
		val (fined, collection) = if (result.fine > 0) payOffBySelling(result.fine, prices, "Fine") else this to null
		return fined.withControlRead(result) to collection
	}

	/** This player with every connection lowered and the reputation set as [result] says (R129). */
	private fun withControlRead(result: ControlResult): Player = copy(
		connections = connections.lowered(result.connectionsLost, result.connectionsLost),
		reputation = result.reputationSet?.let { Reputation(it) } ?: reputation
	)

	/**
	 * This player after backing one whom a reputation control eliminated (R129): [result] takes political and other
	 * connections, and [repLoss] (2d6) reputation, the line telling it.
	 */
	fun afterBackingLost(result: ControlResult, repLoss: Int): LoggedChange =
		copy(connections = connections.lowered(result.backerPoliticalLost, result.backerOthersLost))
			.loseReputation(repLoss, "backing an eliminated corporation")

	/**
	 * This player out of the game (R129): every ship, factory, warehouse, market position, agent, expedition, passenger, the
	 * loan and the money gone. Their reputation and connections stay, for the Corporations tab to show; the game skips them.
	 */
	fun eliminate(): Player = copy(
		money = 0,
		ships = emptyList(),
		marketPositions = emptyList(),
		warehouses = emptyList(),
		factories = emptyList(),
		loan = null,
		agents = emptyList(),
		expeditions = emptyList(),
		passengerGroups = emptyList(),
		eliminated = true
	)

	/** This player's money for the final result (R95): cash less what an open loan still owes; it can be negative. */
	fun finalMoney(): Int = money - (loan?.amount ?: 0)

	// Agents (R103–R108, D25).

	/** This player's [agent], or null when it isn't theirs. */
	fun agent(agent: Agent): RecruitedAgent? = agents.firstOrNull { it.agent == agent }

	/** Where [recruited] stands on the board: its ship's system and location when it's aboard one. */
	private fun placeOf(recruited: RecruitedAgent): AgentPlace = when (val position = recruited.position) {
		is AgentPosition.Aboard -> checkNotNull(ship(position.shipName)).let { AgentPlace(it.system, it.location) }
		is AgentPosition.OnOwn -> AgentPlace(position.system, position.location)
	}

	/** Whether this player has [agent] at [system], anywhere there (R108: "present at a system"). */
	private fun isAgentAt(agent: Agent, system: StarSystem): Boolean = agent(agent)?.let { placeOf(it).system == system } == true

	/** Whether this player has [agent] in a Port, aboard a ship or on its own, at [system] or, when null, at any system. */
	private fun isAgentInPort(agent: Agent, system: StarSystem? = null): Boolean = agent(agent)?.let {
		val place = placeOf(it)
		place.location == ShipLocation.PORT && (system == null || place.system == system)
	} == true

	/** Whether [agent] of this player is aboard [shipName]. */
	private fun isAgentAboard(agent: Agent, shipName: String): Boolean = agent(agent)?.isAboard(shipName) == true

	/** Novak (R108): +4 to the jump result of the ship he travels on, [shipName]; 0 otherwise. */
	fun jumpBonusFor(shipName: String): Int = if (isAgentAboard(Agent.NOVAK, shipName)) Agent.NOVAK_JUMP_BONUS else 0

	/**
	 * What this player's ship [shipName] adds to its landing roll (R116): the ship's own bonus (Aerodynamic, Battle comm)
	 * and Badger's +3 when he's aboard (R108: −3 to the security he faces).
	 */
	fun landingBonusFor(shipName: String): Int = checkNotNull(ship(shipName)).landingBonus() + badgerBonusFor(shipName)

	/** Badger (R108): +3 on the landing roll of the ship carrying him, [shipName]; 0 otherwise. */
	fun badgerBonusFor(shipName: String): Int = if (isAgentAboard(Agent.BADGER, shipName)) Agent.BADGER_SECURITY_REDUCTION else 0

	/**
	 * This player once security confiscated their ship [shipName] with everything aboard (R117): nothing is paid back, and
	 * agents aboard step off into its system's Port and survive (R107).
	 */
	fun confiscateShip(shipName: String): Player {
		val ship = checkNotNull(ship(shipName))
		return withAgentsSteppedOff(ship.landAt(ShipLocation.PORT)).withoutShip(shipName)
	}

	/** This player once security confiscated the illegal goods aboard [shipName] (R117); legal cargo stays. */
	fun confiscateIllegalCargo(shipName: String): Player = withShip(checkNotNull(ship(shipName)).withoutIllegalLots())

	/** Trigger (R108): +1 to intercept for the ship carrying him, [shipName]; 0 otherwise. */
	fun interceptBonusFor(shipName: String): Int = if (isAgentAboard(Agent.TRIGGER, shipName)) Agent.TRIGGER_INTERCEPT_BONUS else 0

	/** X (R108): +5 weapons for the ship carrying him, [shipName]; 0 otherwise. */
	fun weaponsBonusFor(shipName: String): Int = if (isAgentAboard(Agent.X, shipName)) Agent.X_WEAPONS_BONUS else 0

	/** Wolf (R108): +4 on this player's initiative roll, wherever he is; 0 without him. */
	fun initiativeBonus(): Int = if (agent(Agent.WOLF) != null) Agent.WOLF_INITIATIVE_BONUS else 0

	/** Shorty (R108): +4 on both rolls of this player's sabotage at [system] when he's present there; 0 otherwise. */
	fun sabotageBonusAt(system: StarSystem): Int = if (isAgentAt(Agent.SHORTY, system)) Agent.SHORTY_SABOTAGE_BONUS else 0

	/** Silent (R108): whether this player's assets at [system] are immune to sabotage, with him in Port there. */
	fun isImmuneToSabotageAt(system: StarSystem): Boolean = isAgentInPort(Agent.SILENT, system)

	/** Limpy (R108): whether this player may act as Dictator for a good at [system], with him in Port there. */
	fun mayDictateAt(system: StarSystem): Boolean = isAgentInPort(Agent.LIMPY, system)

	/** How many of this player's agents are on their own at each place (R105), in a stable order: public. */
	fun agentsOnOwn(): List<AgentCount> = agents.map { it.position }.filterIsInstance<AgentPosition.OnOwn>()
		.groupingBy { AgentPlace(it.system, it.location) }.eachCount()
		.map { (place, count) -> AgentCount(place, count) }
		.sortedWith(compareBy({ it.place.system.ordinal }, { it.place.location.ordinal }))

	/**
	 * This player after paying [payment] HT to recruit (R103): 10 to 60, and they must have it. When [roll] (2d6) × 5 is at
	 * or below the payment, [picked], drawn at random from the pool, joins them at [start]; otherwise the money is gone.
	 */
	fun recruitAgent(payment: Int?, roll: Int, picked: Agent, start: AgentPosition, turn: Int? = null): Player {
		if (payment == null || payment !in Agent.MIN_RECRUIT_PAYMENT..Agent.MAX_RECRUIT_PAYMENT) {
			throw InvalidAgentException("Pay a whole number from ${Agent.MIN_RECRUIT_PAYMENT} to ${Agent.MAX_RECRUIT_PAYMENT} HT.")
		}
		if (payment > money) {
			throw InvalidAgentException("You have $money HT.")
		}
		checkAgentStart(start)
		val paid = pay(payment, "Agent recruitment")
		return if (roll * Agent.RECRUIT_ROLL_MULTIPLIER <= payment) paid.withAgentAdded(RecruitedAgent(picked, start, turn)) else paid
	}

	/**
	 * [agent], recruited in turn [turn] and not placed yet, starts at [to] instead of where it joined (playtest, 2026-11-21):
	 * anywhere a new agent may start ([checkAgentStart]), once.
	 */
	fun placeNewAgent(agent: Agent?, to: AgentPosition, turn: Int): Player {
		val recruited = agent?.let { agent(it) } ?: throw InvalidAgentException("Choose one of your agents.")
		if (!recruited.isPlaceableIn(turn)) {
			throw InvalidAgentException("${recruited.agent.displayName} has already started: move him like any agent.")
		}
		checkAgentStart(to)
		return withAgent(recruited.placedAt(to))
	}

	/**
	 * Refuses a new agent's [start] unless it's aboard one of this player's ships out of the yard, or on its own in the Port
	 * or on the Planet of a system where they have a ship or a warehouse (R103).
	 */
	fun checkAgentStart(start: AgentPosition) {
		when (start) {
			is AgentPosition.Aboard -> {
				val ship = ship(start.shipName)
				if (ship == null || ship.isInShipyard()) {
					throw InvalidAgentException("Choose one of your ships out of the shipyard.")
				}
			}
			is AgentPosition.OnOwn -> if (ships.none { it.system == start.system && !it.isInShipyard() } && warehouseAt(start.system) == null) {
				throw InvalidAgentException("You need a ship or a warehouse at ${start.system.displayName}.")
			}
		}
	}

	/**
	 * This player after moving their [agent] [to] (R104), at any time like cargo: boarding one of their ships at the same
	 * system and location, stepping off a ship in Port or on a Planet there, or moving between two of their ships at the same
	 * place. Never in Space, where an agent can't stand on its own and ships in a battle are.
	 */
	fun moveAgent(agent: Agent?, to: AgentPosition): Player {
		val recruited = agent?.let { agent(it) } ?: throw InvalidAgentException("Choose one of your agents.")
		val from = placeOf(recruited)
		if (from.isInSpace()) {
			throw InvalidAgentException("Agents can't move in Space: wait until the ship is in Port or on the Planet.")
		}
		when (to) {
			is AgentPosition.Aboard -> {
				val ship = ship(to.shipName)
				if (ship == null || ship.isInShipyard() || ship.system != from.system || ship.location != from.location) {
					throw InvalidAgentException("${recruited.agent.displayName} can board only your ships at the same place.")
				}
				if (recruited.isAboard(ship.name)) {
					throw InvalidAgentException("${recruited.agent.displayName} is already aboard ${ship.name}.")
				}
			}
			is AgentPosition.OnOwn -> if (recruited.position !is AgentPosition.Aboard || AgentPlace(to.system, to.location) != from) {
				throw InvalidAgentException("${recruited.agent.displayName} can step off only where his ship is, in Port or on the Planet.")
			}
		}
		return withAgent(recruited.movedTo(to))
	}

	/** Refuses offering [agent] for sale (R106) unless it's theirs and not aboard a ship in Space. */
	fun checkAgentForSale(agent: Agent?): Agent {
		val recruited = agent?.let { agent(it) } ?: throw InvalidAgentException("Choose one of your agents.")
		if (placeOf(recruited).isInSpace()) {
			throw InvalidAgentException("An agent aboard a ship in Space can't be sold.")
		}
		return recruited.agent
	}

	/** Where [agent] of this player stands, for the buyer of an offer (R106), or null once it's no longer theirs. */
	fun placeOfAgent(agent: Agent): AgentPlace? = agent(agent)?.let { placeOf(it) }

	/**
	 * This player after selling [agent] for [price] (R106): paid, and the agent gone. Where it stood, on its own (stepping
	 * off any ship first), is the buyer's to take.
	 */
	fun sellAgent(agent: Agent, price: Int): Player = receive(price, "Sold an agent").withoutAgent(agent)

	/** This player after buying [agent] for [price] (R106), standing on its own at [place]; refused when they can't pay. */
	fun buyAgent(agent: Agent, price: Int, place: AgentPlace): Player {
		if (price > money) {
			throw InvalidAgentException("This agent costs $price HT; you have $money HT.")
		}
		return pay(price, "Bought an agent").withAgentAdded(RecruitedAgent(agent, AgentPosition.OnOwn(place.system, place.location)))
	}

	/**
	 * This player with the agents aboard [ship] stepped off at its location (R107): sold or destroyed outside Space; one in
	 * Space puts them in that system's Port.
	 */
	private fun withAgentsSteppedOff(ship: Ship): Player {
		val location = if (ship.location == ShipLocation.PORT || ship.location == ShipLocation.PLANET) ship.location else ShipLocation.PORT
		return copy(agents = agents.map { if (it.isAboard(ship.name)) it.movedTo(AgentPosition.OnOwn(ship.system, location)) else it })
	}

	/**
	 * This player after a sabotage's [hits] on their ship [shipName], placed as [placement] (R99): as in battle (R76), with the
	 * POLY and ISO that no longer fits going to their warehouse there as far as it fits, the rest lost. A ship destroyed this
	 * way goes with its cargo, and its agents step off into the Port (R107). Returns the goods lost too.
	 */
	fun takeSabotageHits(shipName: String, hits: Int, placement: HitPlacement): Pair<Player, Map<Goods, Int>> {
		val ship = checkNotNull(ship(shipName))
		val hit = ship.takeHits(hits, placement)
		if (hit.isDestroyed()) {
			return withAgentsSteppedOff(ship).withoutShip(shipName) to ship.cargo
		}
		val warehouse = warehouseAt(ship.system)
		val thrown = placement.jettison.filterValues { it > 0 }
		val kept = warehouse?.let { thrown.entries.fold(it) { stored, (goods, units) -> stored.load(goods, units) } }
		val lost = thrown.mapValues { (goods, units) -> units - ((kept?.holding(goods) ?: 0) - (warehouse?.holding(goods) ?: 0)) }
		val damaged = withShip(hit)
		return (if (kept == null) damaged else damaged.withWarehouse(kept)) to lost.filterValues { it > 0 }
	}

	/**
	 * Refuses [target] as a sabotage on this player's assets (R97) unless it names a ship of theirs in Port at its system and
	 * not parked in a hangar in turn [turn] (R127), or their warehouse or factory there.
	 */
	fun checkSabotageTarget(target: SabotageTarget, turn: Int) {
		when (target) {
			is SabotageTarget.Ship -> if (ship(target.name)?.isInPortAt(target.system) != true) {
				throw InvalidSabotageException("Choose a ship in Port: ships in the yard, on a Planet or in Space can't be sabotaged.")
			} else if (checkNotNull(ship(target.name)).isParkedOn(turn)) {
				throw InvalidSabotageException("${target.name} is in the Parking Hangar: it can't be sabotaged.")
			}
			is SabotageTarget.Warehouse -> if (warehouseAt(target.system) == null) {
				throw InvalidSabotageException("$corporationName has no warehouse at ${target.system.displayName}.")
			}
			is SabotageTarget.Factory -> if (factoryAt(target.system, target.goods) == null) {
				throw InvalidSabotageException("$corporationName has no ${target.goods.name} factory at ${target.system.displayName}.")
			}
		}
	}

	/** This player once a sabotage's hits destroyed their ship [shipName] in Port (R99): it goes with its cargo; its agents step off (R107). */
	private fun destroyedBySabotage(shipName: String): Player {
		val ship = checkNotNull(ship(shipName))
		return withAgentsSteppedOff(ship).withoutShip(shipName)
	}

	/** This player after a sabotage took [lots] from their warehouse at [system] (R99): what it holds, as far as it goes. */
	fun withWarehouseLoss(system: StarSystem, lots: Map<Goods, Int>): Player {
		val warehouse = checkNotNull(warehouseAt(system))
		return withWarehouse(lots.entries.fold(warehouse) { held, (goods, units) -> held.unload(goods, minOf(units, held.holding(goods))) })
	}

	private fun withAgent(recruited: RecruitedAgent): Player = copy(agents = agents.map { if (it.agent == recruited.agent) recruited else it })

	/** This player with [factory] in place of their factory for that good at that system, or added when they had none. */
	private fun withFactory(factory: Factory): Player {
		val others = factories.filterNot { it.isFor(factory.system, factory.goods) }
		return copy(factories = others + factory)
	}

	/** This player set up for the first turn (R40, R82): [money] HT, [ships], and [connectionPoints] starting points to place. */
	fun startWith(money: Int, ships: List<Ship>, connectionPoints: Int): Player =
		copy(money = money, ships = ships, connectionPoints = connectionPoints)

	/** The public log line of this player's starting ship (R132): "Vega Lines starts with Wayfarer at Tau Ceti". */
	fun startingShipLog(): String = "$corporationName starts with ${ships.first().position()}"

	/** Whether this player has a ship called [name], ignoring case: ship names are unique in a game that way (R62). */
	fun hasShipNamed(name: String): Boolean = ships.any { it.name.equals(name, ignoreCase = true) }

	/** This player's ship called [name], or null if they have none by that name. */
	fun ship(name: String?): Ship? = ships.firstOrNull { it.name == name }

	/** This player after [ship] jumped to [destination] (see [Ship.jumpTo]). */
	fun jumpShip(ship: Ship, destination: StarSystem): Player = withShip(ship.jumpTo(destination))

	/** This player after their ship [shipName] ended up at [location] (see [Ship.landAt]). */
	fun landShip(shipName: String, location: ShipLocation): Player = withShip(checkNotNull(ship(shipName)).landAt(location))

	/**
	 * This player's initiative bid of [amount] (R27, S05-1, S05-2): a whole number of HT from 0 up to their money.
	 */
	fun bid(amount: Int?): InitiativeBid {
		if (amount == null) {
			throw InvalidBidException("Enter a whole number of HT, 0 or more.")
		}
		if (amount < 0) {
			throw InvalidBidException("A bid can't be negative. Enter 0 to bid nothing.")
		}
		if (amount > money) {
			throw InvalidBidException("You have $money HT. Enter $money or less.")
		}
		return InitiativeBid(email, amount)
	}

	/**
	 * This player's secret offer (R17), valid on its own ([Offer.place]) and never for a good they're Dictator of at
	 * [system]: they place that offer when it resolves in Trading (R38).
	 */
	fun offer(system: StarSystem, goods: Goods, type: OfferType, price: Int?): Offer {
		val offer = Offer.place(email, system, goods, type, price)
		if (isDictatorOf(system, goods)) {
			throw InvalidOfferException("You're Dictator of ${goods.name} here — you place that offer when it resolves in Trading.")
		}
		return offer
	}

	/**
	 * This player's offer as Dictator of [good], placed when it resolves in Trading (R38, E05-S02-2): checked like any
	 * offer (R03). Being the Dictator asked is checked by the resolution, which asked them.
	 */
	fun dictatorOffer(good: MarketGood, type: OfferType, price: Int?): Offer = Offer.place(email, good.system, good.goods, type, price)

	/** Whether this player is Wholesaler of [good] at its system (R28, R37). */
	fun isWholesalerOf(good: MarketGood): Boolean =
		marketPositions.any { it.system == good.system && it.goods == good.goods && it.tier == MarketTier.WHOLESALER }

	/** Whether this player is Dictator of [goods] at [system] (R28, R38). */
	fun isDictatorOf(system: StarSystem, goods: Goods): Boolean =
		marketPositions.any { it.system == system && it.goods == goods && it.tier == MarketTier.DICTATOR }

	/** This player's position for [good] at its system, or null when they hold none there (R28, R126). */
	fun positionAt(good: MarketGood): MarketPosition? = marketPositions.firstOrNull { it.isFor(good) }

	/**
	 * This player after a challenge took their position for [good] one tier down (R126): Dictator to Wholesaler, Wholesaler to
	 * Retailer, a Retailer to none.
	 */
	fun withPositionLowered(good: MarketGood): Player =
		copy(marketPositions = marketPositions.mapNotNull { if (it.isFor(good)) it.lowered() else it })

	/** This player after paying [MarketChallenge.FEE] for a challenge (R126); refused without the money. */
	fun payChallengeFee(): Player {
		if (money < MarketChallenge.FEE) {
			throw InvalidChallengeException("Challenging a market position costs ${MarketChallenge.FEE} HT; you have $money HT.")
		}
		return pay(MarketChallenge.FEE, "Challenged a market position")
	}

	/** This player's tier for [good] at its system, or null when they hold none there (R28, R40). */
	fun tierAt(good: MarketGood): MarketTier? = marketPositions.firstOrNull { it.isFor(good) }?.tier

	/** Whether a position roll can move this player up for [good] (E05-S06-3): not once they're its Dictator. */
	fun canRiseAt(good: MarketGood): Boolean = tierAt(good) != MarketTier.DICTATOR

	/**
	 * The tier a won position roll gives this player for [good] (R43): one up from the tier they hold, none → Retailer →
	 * Wholesaler → Dictator. Only for a player who [canRiseAt] it.
	 */
	fun promotionAt(good: MarketGood): MarketTier =
		tierAt(good)?.let { checkNotNull(it.next()) { "A Dictator can't go higher" } } ?: MarketTier.RETAILER

	/**
	 * This player after winning a position roll for [good]: they hold [promotionAt] it at once (E05-S06-5). It changes
	 * nothing for the rest of the turn, since each good resolves once a turn and its Dictator and Wholesalers are fixed
	 * when it starts. Every rise, none → Retailer included, adds a point of business connections, up to 10 (R83, E12-S02-6).
	 */
	fun promotedAt(good: MarketGood): Player = copy(
		connections = connections.afterMarketRise(),
		reputation = reputation.changedBy(MARKET_RISE_REPUTATION)
	).withPosition(MarketPosition(good.system, good.goods, promotionAt(good)))

	/** Whether this player is Dictator of [good] at its system (R28). */
	fun isDictatorOf(good: MarketGood): Boolean = tierAt(good) == MarketTier.DICTATOR

	/** This player as Wholesaler of [good], once someone else became its Dictator (S06-3). */
	fun demotedAt(good: MarketGood): Player = copy(
		reputation = reputation.changedBy(-reputationLossAfterAgents(MARKET_RISE_REPUTATION))
	).withPosition(MarketPosition(good.system, good.goods, MarketTier.WHOLESALER))

	/** This player holding [position], in place of whatever they held for that good at that system. */
	private fun withPosition(position: MarketPosition): Player {
		val good = MarketGood(position.system, position.goods)
		return copy(marketPositions = marketPositions.filterNot { it.isFor(good) } + position)
	}

	/** Where this player trades from at [system], for a message: "ships in port", or "ships in port or warehouse" with a warehouse there (R49). */
	private fun holdingPlaces(system: StarSystem): String = if (warehouseAt(system) == null) "ships in port" else "ships in port or warehouse"

	/** This player with [hold], one of their ships or warehouses, in place of the one it was. */
	private fun withCargoHold(hold: CargoHold): Player = when (hold) {
		is Ship -> withShip(hold)
		is Warehouse -> withWarehouse(hold)
	}

	private fun withShip(ship: Ship): Player = copy(ships = ships.map { if (it.name == ship.name) ship else it })

	/** This player with [warehouse] in place of the one at its system, or added when they had none there. */
	private fun withWarehouse(warehouse: Warehouse): Player {
		val others = warehouses.filterNot { it.system == warehouse.system }
		val kept = warehouses.map { if (it.system == warehouse.system) warehouse else it }
		return copy(warehouses = if (others.size == warehouses.size) others + warehouse else kept)
	}

	companion object {
		/** R110: what learning one tip-off costs. */
		const val TIP_OFF_FEE = 5

		/** R90: what each ship and each warehouse block adds to the borrowing cap. */
		const val LOAN_PER_SHIP = 100
		const val LOAN_PER_BLOCK = 20

		/** R92: what a turn's unpaid interest costs in reputation. */
		const val UNPAID_INTEREST_REPUTATION = 4

		/** R86: what a rise in market position adds to reputation, and a fall (a Dictator demoted) takes away. */
		const val MARKET_RISE_REPUTATION = 2

		/** A player in the lobby: money, ships and connection points arrive when the game starts (R40, R82); reputation starts at 20 (R85). */
		fun newcomer(email: String, name: String, corporationName: String) = Player(
			email,
			name,
			corporationName,
			0,
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			Connections.NONE,
			0,
			Reputation.INITIAL,
			null,
			emptyList()
		)
	}
}
