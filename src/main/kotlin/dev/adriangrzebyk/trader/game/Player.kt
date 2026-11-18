package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's membership in one game; [email] is the membership key. [money] is private to its owner (R29);
 * [marketPositions] are public (R28), and a player holds none until they earn one (R40, R43). [warehouses] and [factories]
 * are public too (R50), and nobody starts with any (R40, R50).
 */
data class Player(
	val email: String,
	val name: String,
	val corporationName: String,
	val money: Int,
	val ships: List<Ship>,
	val marketPositions: List<MarketPosition>,
	val warehouses: List<Warehouse>,
	val factories: List<Factory>
) {

	init {
		require(warehouses.map { it.system }.toSet().size == warehouses.size) { "A player has at most one warehouse per system." }
	}

	/** This player after paying [amount] HT. */
	fun pay(amount: Int): Player = copy(money = money - amount)

	/** This player after being paid [amount] HT. */
	fun receive(amount: Int): Player = copy(money = money + amount)

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
			OfferType.BUY -> stocked.pay(amount * offer.price)
			OfferType.SELL -> stocked.receive(amount * offer.price)
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

	/** Whether this player has a factory, so they collect output at the start of Investment (R47, E06-S03-6). */
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
			if (group.sumOf { it.units } > factory.size) {
				throw InvalidCollectException("Your ${factory.goods.name} factory at ${factory.system.displayName} makes ${factory.size} units.")
			}
		}
		return placements.fold(this) { player, placement -> player.stored(placement) }
	}

	/**
	 * The public log lines of the collect that turned [before] into this player (R50, E06-S07-1), one per factory: what
	 * was stored is what they now hold more of at the factory's system, and the rest of its output was lost.
	 */
	fun collectLog(before: Player): List<String> = factories.map { factory ->
		factory.collectLog(corporationName, holdingAt(factory.system, factory.goods) - before.holdingAt(factory.system, factory.goods))
	}

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
		return pay(cost.toInt()).withWarehouse(warehouse)
	}

	/**
	 * This player after buying [units] of production of [goods] at [system] (R46, E06-S05): added to their factory there,
	 * or creating it, for [units] × (5 × the good's price in [prices] + 10) HT ([ProductionCost]). The system must be able to produce the
	 * good ([StarSystem.producibleGoods]) and they need enough money; no ship and no factory there already (creator, 2026-11-16).
	 */
	fun buyFactoryUnits(system: StarSystem, goods: Goods, units: Int, prices: MarketPrices): Player {
		if (units < 1) {
			throw InvalidInvestmentException("Enter a whole number of units, 1 or more.")
		}
		if (goods !in system.producibleGoods) {
			throw InvalidInvestmentException("${system.displayName} can't produce ${goods.name}.")
		}
		val cost = ProductionCost(prices.priceOf(MarketGood(system, goods))).of(units)
		if (cost > money) {
			throw InvalidInvestmentException("$units ${if (units == 1) "unit costs" else "units cost"} $cost HT; you have $money HT.")
		}
		val factory = factoryAt(system, goods)?.addUnits(units) ?: Factory(system, goods, units)
		return pay(cost.toInt()).withFactory(factory)
	}

	/**
	 * This player after buying [ship] (R61), fresh from [Ship.commission]: it costs [Ship.price], which they must be able to
	 * pay in full. No ship of theirs needs to be at the yard: in Investment a player is everywhere.
	 */
	fun buyShip(ship: Ship): Player {
		val cost = ship.price()
		if (cost > money) {
			throw InvalidInvestmentException("This ship costs $cost HT; you have $money HT.")
		}
		return copy(money = money - cost, ships = ships + ship)
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
		return pay(cost).withShip(refitted)
	}

	/** This player after removing one [module] from their ship [shipName] in port or in the yard (R65), paid half its price back. */
	fun removeModule(shipName: String, module: Module): Player {
		val ship = shipToRefit(shipName)
		return receive(module.resaleValue()).withShip(ship.withModuleRemoved(module))
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
		return pay(cost).withShip(replaced)
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
		val sold = copy(money = money + ship.saleValue(), ships = ships.filterNot { it.name == ship.name })
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
		return pay(cost).withShip(repaired)
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

	/** This player without their ship [shipName], destroyed in a battle with everything aboard (R77). */
	fun loseShip(shipName: String): Player = copy(ships = ships.filterNot { it.name == shipName })

	/** This player's ships that can jump (R64): every one but those still in the shipyard. */
	fun jumpableShips(): List<Ship> = ships.filterNot { it.isInShipyard() }

	/** This player's ships that leave the yard in turn [turn]'s Investment (R63). */
	fun shipsLeavingYardIn(turn: Int): List<Ship> = ships.filter { it.leavesYardIn(turn) }

	/** This player once their ships bought before turn [turn] left the yard (R63), each into the Port of its system. */
	fun releaseShipsFromYard(turn: Int): Player = copy(ships = ships.map { if (it.leavesYardIn(turn)) it.leaveYard() else it })

	/** This player with [factory] in place of their factory for that good at that system, or added when they had none. */
	private fun withFactory(factory: Factory): Player {
		val others = factories.filterNot { it.isFor(factory.system, factory.goods) }
		return copy(factories = others + factory)
	}

	/** This player set up for the first turn (R40): [money] HT and [ships]. */
	fun startWith(money: Int, ships: List<Ship>): Player = copy(money = money, ships = ships)

	/** Where this player's ships start, for the public log: "Corp — Wayfarer at Tau Ceti, Lantern at Mu Herculis". */
	fun startingPositions(): String = "$corporationName — " + ships.joinToString(", ") { it.position() }

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
	 * when it starts.
	 */
	fun promotedAt(good: MarketGood): Player = withPosition(MarketPosition(good.system, good.goods, promotionAt(good)))

	/** Whether this player is Dictator of [good] at its system (R28). */
	fun isDictatorOf(good: MarketGood): Boolean = tierAt(good) == MarketTier.DICTATOR

	/** This player as Wholesaler of [good], once someone else became its Dictator (S06-3). */
	fun demotedAt(good: MarketGood): Player = withPosition(MarketPosition(good.system, good.goods, MarketTier.WHOLESALER))

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
		/** A player in the lobby: money and ships arrive when the game starts (R40). */
		fun newcomer(email: String, name: String, corporationName: String) =
			Player(email, name, corporationName, 0, emptyList(), emptyList(), emptyList(), emptyList())
	}
}
