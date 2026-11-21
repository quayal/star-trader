package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.battle.InvalidBattleException
import dev.adriangrzebyk.trader.game.news.OpportunityTerms
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TipOff

/**
 * A player's ship: a [hull] with [modules] (R51, R52). Its name and position are public, like the board; its [cargo]
 * (units per good) is private to its owner (S02-7), as are its hull and modules (R57). Trading fills and empties the
 * cargo (E05). Its stats are derived from the hull and modules, never stored (R53). All its crew are of one [crewClass]
 * (R67). A ship bought in Investment ([boughtOnTurn]; null for every other ship) waits in the shipyard until the next
 * turn's Investment (R63). Battle damage (R76) stays until repaired (R79): [hullDamage] hits on the hull, and
 * [damagedModules], fitted modules that took one hit and count as not there (a multiset within [modules]). [specialLots] are
 * goods bought through opportunities (R112) and [deliveryJob] the delivery it's carrying (R114), both private like cargo.
 */
data class Ship(
	val name: String,
	override val system: StarSystem,
	val location: ShipLocation,
	override val cargo: Map<Goods, Int>,
	val hull: Hull,
	val modules: List<Module>,
	val crewClass: CrewClass,
	val boughtOnTurn: Int?,
	val hullDamage: Int,
	val damagedModules: List<Module>,
	val specialLots: List<SpecialLot> = emptyList(),
	val deliveryJob: TipOff? = null
) : CargoHold {

	init {
		hull.checkFits(modules)
		require(damagedModules.distinct().all { module -> damagedModules.count { it == module } <= modules.count { it == module } }) {
			"$name has more damaged modules than fitted ones."
		}
	}

	/** The fitted modules that work (R76): every one but the damaged ones, which count as not there. */
	fun workingModules(): List<Module> {
		val damaged = damagedModules.toMutableList()
		return modules.filterNot { damaged.remove(it) }
	}

	/** R53, R76: the hull's weapons plus every working module's. */
	fun weapons(): Int = hull.weapons + workingModules().sumOf { it.weapons }

	/** R53, R76: the rocket weapons of every working module (the hull has none). */
	fun rocketWeapons(): Int = workingModules().sumOf { it.rocketWeapons }

	/**
	 * What this ship adds to 2d6 when it fires (R75): its [weapons], or with [rockets] only its working modules' rocket
	 * values (creator, 2026-11-17: the hull and modules without one add nothing to a rocket salvo).
	 */
	fun firepower(rockets: Boolean): Int = if (rockets) rocketWeapons() else weapons()

	/** Refuses an order to fire rockets from a ship with no working rocket module (R75). */
	fun checkCanFireRockets() {
		if (rocketWeapons() == 0) {
			throw InvalidBattleException("$name has no working rocket module, so it can't fire rockets.")
		}
	}

	/** This ship's interception total with [d6] rolled (R70, R71): intercept + d6 + crew class + a working Battle comm. */
	fun interceptTotal(d6: Int): Int = intercept() + d6 + interceptModifier()

	/** What this ship's crew and a working Battle comm add to its interception rolls (R71). */
	fun interceptModifier(): Int = crewClass.interceptModifier + if (hasWorkingBattleComm()) BATTLE_COMM_INTERCEPT_BONUS else 0

	/** Whether this ship has a working Battle comm module: +1 to intercept (R71), and an interception try costs no reputation (R86). */
	fun hasWorkingBattleComm(): Boolean = Module.BATTLE_COMM in workingModules()

	/** The public log's account of an interception roll (R70), with [bonus] from an agent aboard (Trigger, R108): "Lantern (2 + d6 4 + 0 = 6)". */
	fun interceptLog(d6: Int, bonus: Int): String {
		val modifier = interceptModifier() + bonus
		val sign = if (modifier < 0) "− ${-modifier}" else "+ $modifier"
		return "$name (${intercept()} + d6 $d6 $sign = ${interceptTotal(d6) + bonus})"
	}

	/** Whether this ship has battle damage to repair (R79). */
	fun isDamaged(): Boolean = hullDamage > 0 || damagedModules.isNotEmpty()

	/** Whether the hull took more hits than its shields (R76): the ship is destroyed. */
	fun isDestroyed(): Boolean = hullDamage > shields()

	/** How many of [module] are fitted and damaged (R76). */
	fun damagedCount(module: Module): Int = damagedModules.count { it == module }

	/**
	 * The most hits this ship can take and survive (R76): the hull up to its shields, 2 for each working module and 1 for
	 * each damaged one. More than that destroys it whatever its owner chooses.
	 */
	fun hitsSurvivable(): Int = (shields() - hullDamage).coerceAtLeast(0) + workingModules().size * 2 + damagedModules.size

	/**
	 * This ship after its owner placed [hits] hits as [placement] (R76). Refused unless the placement adds up to [hits],
	 * names only modules fitted in that condition, gives a working module 1 or 2 and a damaged one 1, and gives the hull
	 * at most what destroys it; and unless [placement]'s jettison is exactly the POLY and ISO that no longer fits. The
	 * result may be destroyed ([isDestroyed]).
	 */
	fun takeHits(hits: Int, placement: HitPlacement): Ship {
		if (placement.total() != hits) {
			throw InvalidBattleException("Place exactly $hits hits on $name, not ${placement.total()}.")
		}
		if (placement.hullHits !in 0..shields() + 1 - hullDamage) {
			throw InvalidBattleException("$name's hull can take at most ${shields() + 1 - hullDamage} more hits.")
		}
		val working = workingModules().toMutableList()
		val damaged = damagedModules.toMutableList()
		var fitted = modules
		for (hit in placement.moduleHits) {
			val pool = if (hit.alreadyDamaged) damaged else working
			if (!pool.remove(hit.module)) {
				val condition = if (hit.alreadyDamaged) "damaged" else "working"
				throw InvalidBattleException("$name has no more $condition ${hit.module.displayName} modules to hit.")
			}
			if (hit.hits !in 1..(if (hit.alreadyDamaged) 1 else 2)) {
				throw InvalidBattleException("A module takes 1 or 2 hits, and a damaged one 1.")
			}
			if (hit.alreadyDamaged || hit.hits == 2) {
				fitted = fitted - hit.module
			} else {
				damaged += hit.module
			}
		}
		// What `damaged` holds now: the damaged modules not hit again, plus the working ones just damaged.
		val hit = copy(hullDamage = hullDamage + placement.hullHits, modules = fitted, damagedModules = damaged)
		return hit.jettisoned(placement.jettison)
	}

	/** This ship after throwing [jettison] overboard, refused unless it's exactly what no longer fits (R76). */
	private fun jettisoned(jettison: Map<Goods, Int>): Ship {
		val overflow = (cargoSpacesUsed() - cargoSpaces()).coerceAtLeast(0)
		val thrown = jettison.filterValues { it > 0 }
		if (thrown.keys.any { !it.takesCargoSpace } ||
			thrown.values.sum() != overflow ||
			thrown.any { (goods, units) -> units > holding(goods) }
		) {
			throw InvalidBattleException(
				if (overflow == 0) "$name keeps all its cargo." else "$name loses $overflow units of POLY and ISO: choose which."
			)
		}
		return thrown.entries.fold(this) { ship, (goods, units) -> ship.withCargo(goods, ship.holding(goods) - units) }
	}

	/** What repairing all this ship's damage costs (R79): the hull's repair price if it's hit, plus each damaged module's. */
	fun repairCost(): Int = (if (hullDamage > 0) hull.repairCost else 0) + damagedModules.sumOf { it.repairCost() }

	/** This ship with all its damage repaired (R79), refused when it has none. */
	fun repaired(): Ship {
		if (!isDamaged()) {
			throw InvalidInvestmentException("$name has no damage to repair.")
		}
		return copy(hullDamage = 0, damagedModules = emptyList())
	}

	/** R53: the crew the hull and every module need. */
	fun crewNeeded(): Int = hull.crew + modules.sumOf { it.crew }

	/** R53, R76: the hull's built-in cargo spaces plus every working module's. */
	fun cargoSpaces(): Int = hull.builtInCargo + workingModules().sumOf { it.cargoSpaces }

	/** R53, R76: the passengers every working module takes. */
	fun passengers(): Int = workingModules().sumOf { it.passengers }

	/** R53: the hull's shields. */
	fun shields(): Int = hull.shields

	/** R53: the hull's intercept. */
	fun intercept(): Int = hull.intercept

	/** R53: how many modules the hull holds. */
	fun moduleCapacity(): Int = hull.moduleCapacity

	/** R53: the modules that take module capacity (not Safe jump, R52). */
	fun modulesUsed(): Int = modules.count { it.takesModuleCapacity }

	/** Each different module fitted and how many of it, in the order they were first fitted (E07-S03-2). */
	fun fittedModules(): Map<Module, Int> = modules.groupingBy { it }.eachCount()

	/**
	 * R42, R54: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. Weapons take one each (R112), and a
	 * delivery in a Cargo module takes the module's two spaces (R114).
	 */
	fun cargoSpacesUsed(): Int =
		cargo.filterKeys { it.takesCargoSpace }.values.sum() + specialLots.sumOf { it.cargoSpaces() } + deliverySpace(Module.CARGO)

	/** The passenger seats taken (R112, R114): one per slave, and a delivery's whole Passenger module. */
	fun passengerSeatsUsed(): Int = specialLots.sumOf { it.passengerSeats() } + deliverySpace(Module.PASSENGER)

	/** Free passenger seats (R52). */
	fun freePassengerSeats(): Int = (passengers() - passengerSeatsUsed()).coerceAtLeast(0)

	/** The spaces of a whole [module] taken by this ship's delivery, if it travels in one (R114). */
	private fun deliverySpace(module: Module): Int =
		if ((deliveryJob?.opportunity as? OpportunityTerms.Delivery)?.space == module) module.cargoSpaces + module.passengers else 0

	/** What this ship adds to its landing roll (R116): +2 for an Aerodynamic hull, +3 for a working Battle comm module. */
	fun landingBonus(): Int =
		(if (hull.aerodynamic) AERODYNAMIC_LANDING_BONUS else 0) + if (hasWorkingBattleComm()) BATTLE_COMM_LANDING_BONUS else 0

	/** Whether it has a military hull or module (R56): forced into a Port, it's confiscated (R117). */
	fun isMilitary(): Boolean = hull.military || modules.any { it.military }

	/** Whether it carries anything illegal (R112) or is military (R56): what the landing choice warns about. */
	fun isContraband(): Boolean = isMilitary() || specialLots.any { it.isIllegal() }

	/**
	 * This ship with [lot] aboard (R112): weapons need free cargo spaces and slaves free passenger seats; what doesn't fit
	 * can't be bought, so it's refused.
	 */
	fun withSpecialLot(lot: SpecialLot): Ship {
		if (lot.cargoSpaces() > (cargoSpaces() - cargoSpacesUsed()).coerceAtLeast(0)) {
			throw InvalidGameOperationException(
				"$name has room for ${(cargoSpaces() - cargoSpacesUsed()).coerceAtLeast(0)} units of ${lot.goods.displayName}."
			)
		}
		if (lot.passengerSeats() > freePassengerSeats()) {
			throw InvalidGameOperationException("$name has ${freePassengerSeats()} free passenger seats for slaves.")
		}
		return copy(specialLots = specialLots + lot)
	}

	/** The units of [goods] bound for [system] aboard (R112): what one sale there sells. */
	fun specialUnits(goods: SpecialGoods, system: StarSystem): Int = specialLots.filter { it.isFor(goods, system) }.sumOf { it.units }

	/** This ship once its [goods] bound for [system] were sold (R112). */
	fun withoutSpecial(goods: SpecialGoods, system: StarSystem): Ship = copy(specialLots = specialLots.filterNot { it.isFor(goods, system) })

	/**
	 * This ship carrying the delivery of [tipOff] (R114) in one whole module of its [space]: refused when it already
	 * carries one, has no such module, or hasn't the module's room free.
	 */
	fun withDelivery(tipOff: TipOff, space: Module): Ship {
		if (deliveryJob != null) {
			throw InvalidGameOperationException("$name already carries a delivery.")
		}
		val free = if (space == Module.PASSENGER) freePassengerSeats() else (cargoSpaces() - cargoSpacesUsed()).coerceAtLeast(0)
		if (space !in workingModules() || free < space.cargoSpaces + space.passengers) {
			throw InvalidGameOperationException("$name needs a whole free ${space.displayName} module for this.")
		}
		return copy(deliveryJob = tipOff)
	}

	/** This ship once its delivery arrived (R114). */
	fun delivered(): Ship = copy(deliveryJob = null)

	/** This ship with military [added] modules fitted on a Planet through an opportunity (R113), refused when they don't fit. */
	fun withMilitaryModules(added: List<Module>): Ship {
		hull.checkFits(modules + added)
		return copy(modules = modules + added)
	}

	/** This ship back from the expedition (R114), at [location] in [system]. */
	fun returnedTo(system: StarSystem, location: ShipLocation): Ship = copy(system = system, location = location)

	/** Whether this ship has no military parts (R56): the expedition takes only a legal ship (R114). */
	fun isLegal(): Boolean = !isMilitary()

	/** This ship without its illegal goods (R117: confiscated when forced into a Port); its legal cargo stays. */
	fun withoutIllegalLots(): Ship = copy(specialLots = specialLots.filterNot { it.isIllegal() })

	/** Whether this ship is in port, at whatever system: the only place it loads from or unloads into a warehouse (R58). */
	fun isInPort(): Boolean = location == ShipLocation.PORT

	/** Whether this ship is still in the shipyard it was bought at (R60, R64): it doesn't jump, trade or take cargo. */
	fun isInShipyard(): Boolean = location == ShipLocation.SHIPYARD

	/** Whether this ship can be refitted (R65): in the yard, or in Port at any system (creator, 2026-11-17: not only at shipyards). */
	fun canBeRefitted(): Boolean = isInShipyard() || isInPort()

	/** Whether this ship can be repaired (R79): in the yard, or in Port at a system with a shipyard. */
	fun canBeRepaired(): Boolean = isInShipyard() || (isInPort() && system.hasShipyard())

	/** The turn whose Investment this ship leaves the yard in (R63): the one after it was bought; null once it's out. */
	fun leavesYardOnTurn(): Int? = boughtOnTurn?.takeIf { isInShipyard() }?.plus(1)

	/** Whether this ship leaves the yard in turn [turn]'s Investment (R63). */
	fun leavesYardIn(turn: Int): Boolean = leavesYardOnTurn()?.let { it <= turn } ?: false

	/** This ship once it left the yard (R63): in the Port of its shipyard's system. */
	fun leaveYard(): Ship = copy(location = ShipLocation.PORT, boughtOnTurn = null)

	/** The public log line when this ship leaves the yard (R63): "Spiral leaves the Tau Ceti shipyard". */
	fun leavingYardLog(): String = "$name leaves the ${system.displayName} shipyard"

	/** What this ship costs to commission (R61): its hull, every module and its crew at its class. */
	fun price(): Int = hull.price + modules.sumOf { it.price } + crewNeeded() * crewClass.pricePerCrew

	/**
	 * This ship with its whole crew replaced by one of [crewClass] (R89), refused for the class it already has. Where the
	 * ship is and paying for it ([crewCost]) are the player's to check.
	 */
	fun withCrew(crewClass: CrewClass): Ship {
		if (crewClass == this.crewClass) {
			throw InvalidInvestmentException("$name's crew is already class ${crewClass.name}.")
		}
		return copy(crewClass = crewClass)
	}

	/** What a new crew of [crewClass] costs this ship (R89): every crew member it needs, at that class. The old crew isn't paid back. */
	fun crewCost(crewClass: CrewClass): Int = crewNeeded() * crewClass.pricePerCrew

	/** What selling this ship pays (R66): half its hull's and modules' prices, rounded down; its crew isn't paid back. */
	fun saleValue(): Int = (hull.price + modules.sumOf { it.price }) / 2

	/**
	 * This ship with one more [module] (R65), refused for a military one (R56) or one the hull can't take (R52). Where the
	 * ship is and paying for it are the player's to check.
	 */
	fun withModuleAdded(module: Module): Ship {
		if (!module.isForSale()) {
			throw InvalidInvestmentException("${module.displayName} is military: it can't be bought.")
		}
		return copy(modules = modules + module)
	}

	/**
	 * This ship with one [module] fewer (R65), refused when it has none, or when what's left couldn't hold the POLY and
	 * ISO it carries (R42, R54): goods are never lost to a refit.
	 */
	fun withModuleRemoved(module: Module): Ship {
		if (module !in modules) {
			throw InvalidInvestmentException("$name has no ${module.displayName} module.")
		}
		val fitted = modules - module
		// A working one goes first; only when every one of them is damaged does a damaged one go.
		val damaged = if (damagedCount(module) > fitted.count { it == module }) damagedModules - module else damagedModules
		val refitted = copy(modules = fitted, damagedModules = damaged)
		if (refitted.cargoSpaces() < cargoSpacesUsed()) {
			throw InvalidInvestmentException(
				"$name carries ${cargoSpacesUsed()} units of POLY and ISO; without that module it would have only " +
					"${refitted.cargoSpaces()} cargo spaces."
			)
		}
		return refitted
	}

	/** Whether this ship is at [location] of [system]. */
	fun isAt(system: StarSystem, location: ShipLocation): Boolean = this.system == system && this.location == location

	/** Whether this ship is in port at [system], where it can trade (R23, R42). */
	fun isInPortAt(system: StarSystem): Boolean = this.system == system && location == ShipLocation.PORT

	override fun cargoName(): String = name

	override fun lots(): List<GoodsLot> =
		cargo.filterValues { it > 0 }.toSortedMap().map { (goods, units) -> GoodsLot(goods, units, system, name) }

	/** How many units of [goods] this ship carries. */
	override fun holding(goods: Goods): Int = cargo[goods] ?: 0

	/** The free cargo spaces that matter for [goods] (R42): the free space for POLY and ISO, null for CHIPS and FOOD, which take none. */
	fun freeSpacesFor(goods: Goods): Int? = if (goods.takesCargoSpace) freeCargoSpaces() else null

	/** How many of [units] of [goods] fit (R42): POLY and ISO up to the free cargo space, CHIPS and FOOD all. */
	override fun unitsThatFit(goods: Goods, units: Int): Int = freeSpacesFor(goods)?.let { minOf(units, it) } ?: units

	/** Free cargo spaces (R42), never below 0, even for a ship that somehow carries more than it holds. */
	private fun freeCargoSpaces(): Int = (cargoSpaces() - cargoSpacesUsed()).coerceAtLeast(0)

	/** This ship after loading [units] of [goods] it bought: what fits ([unitsThatFit]); the rest is lost (R16, R42). */
	override fun load(goods: Goods, units: Int): Ship = withCargo(goods, holding(goods) + unitsThatFit(goods, units))

	/** This ship after unloading [units] of [goods] it sold (R10), refused when it holds fewer. */
	override fun unload(goods: Goods, units: Int): Ship {
		if (units > holding(goods)) {
			throw InvalidTradeException("$name has only ${holding(goods)} ${goods.name}.")
		}
		return withCargo(goods, holding(goods) - units)
	}

	private fun withCargo(goods: Goods, units: Int): Ship {
		val others = cargo - goods
		return copy(cargo = if (units > 0) others + (goods to units) else others)
	}

	/**
	 * This ship's chance to jump to [destination] (R68): the route's base chance plus its crew's modifier and its working
	 * Safe jump module's, if it has one (a damaged one counts as not there, R76). Null for the system it's already at,
	 * where a move into its Space always succeeds (R69).
	 */
	fun jumpChanceTo(destination: StarSystem): JumpChance? = system.baseJumpChanceTo(destination)?.let { base ->
		JumpChance(base + crewClass.jumpModifier + workingModules().sumOf { it.jumpModifier })
	}

	/** This ship's chance to jump to each other system (R68), in map order: what its player sees before jumping. */
	fun jumpChances(): Map<StarSystem, JumpChance> =
		StarSystem.entries.mapNotNull { destination -> jumpChanceTo(destination)?.let { destination to it } }.toMap()

	/**
	 * This ship's chance to jump to [destination] (R68), refused while it's still in the shipyard (R64) or for the system
	 * it's already at (S02-4).
	 */
	fun checkedJumpChanceTo(destination: StarSystem): JumpChance {
		if (isInShipyard()) {
			throw InvalidJumpException("$name is still in the shipyard.")
		}
		return jumpChanceTo(destination)
			?: throw InvalidJumpException("$name is already at ${destination.displayName}. Pick another destination.")
	}

	/**
	 * This ship after a jump to [destination] that succeeded (R19, R68, S02), or a move within the system it's at, into its
	 * Space (R69): instantly, arriving in Space until its player chooses where it ends up (S03). Refused in the shipyard.
	 */
	fun jumpTo(destination: StarSystem): Ship {
		if (destination == system) {
			if (isInShipyard()) {
				throw InvalidJumpException("$name is still in the shipyard.")
			}
		} else {
			checkedJumpChanceTo(destination)
		}
		return copy(system = destination, location = ShipLocation.SPACE)
	}

	/** This ship after its player chose where in its system it ends up (R30, S03): never the shipyard (R60). */
	fun landAt(location: ShipLocation): Ship {
		if (!location.isLandable()) {
			throw InvalidLandingException()
		}
		return copy(location = location)
	}

	/** Whether this ship is in Space at [system] (R70, R73): where it can intercept and fight. */
	fun isInSpaceAt(system: StarSystem): Boolean = this.system == system && location == ShipLocation.SPACE

	/** Where this ship is, for the public log: "Wayfarer at Tau Ceti". */
	fun position(): String = "$name at ${system.displayName}"

	/** The public log's account of the jump that brought this ship here (S02-7): "Wayfarer → Tau Ceti". */
	fun jumpLog(): String = "$name → ${system.displayName}"

	/**
	 * The public log's account of a jump to [destination] that failed on [roll] (R68): "Wayfarer failed to jump to Tau
	 * Ceti (rolled 9) and stays at Gamma Leporis". The target isn't told: it would reveal the crew and modules (R57).
	 */
	fun failedJumpLog(destination: StarSystem, roll: Int): String =
		"$name failed to jump to ${destination.displayName} (rolled $roll) and stays at ${system.displayName}"

	/** The public log's account of where this ship ended up (S03-3): "Wayfarer lands at the port (Tau Ceti)". */
	fun landingLog(): String = "$name ${location.landingLog} (${system.displayName})"

	companion object {
		/**
		 * The suggested ship names (R62): this game's own twelve, which starting ships take two each in join order (R40),
		 * then the original game's ship tokens, which a ship bought later is suggested first. A suggestion is a name nobody
		 * in the game uses yet.
		 */
		val NAMES = listOf(
			"Wayfarer", "Lantern", "Meridian", "Halcyon", "Bright Reach", "Corvid", "Ironside", "Tallow", "Ostrava", "Kestrel",
			"Solace", "Pilgrim", "Spiral", "Coral", "Knight", "Needle", "Sphinx", "Quasar", "Flash", "Luna", "Alfa", "Falcon",
			"Pulsar", "Tzar", "Satyr", "Regent"
		)

		/** R62: a ship's name is 1 to 20 characters once trimmed. */
		const val MAX_NAME_LENGTH = 20

		/** R116: what an Aerodynamic hull and a working Battle comm add to a landing roll. */
		const val AERODYNAMIC_LANDING_BONUS = 2
		const val BATTLE_COMM_LANDING_BONUS = 3

		/** R71: what a working Battle comm module adds to its ship's interception rolls. */
		const val BATTLE_COMM_INTERCEPT_BONUS = 1

		/** R62: letters, digits, spaces, hyphens, apostrophes and full stops, with at least one letter or digit. */
		private val NAME_PATTERN = Regex("[\\p{L}\\p{N} '.-]+")
		const val NAME_CHARACTERS_MESSAGE = "Use letters, digits, spaces, hyphens, apostrophes and full stops."

		/**
		 * A new, empty ship called [name] (trimmed), bought in turn [turn] at [shipyard] (R61): a civilian [hull] with
		 * civilian [modules] it can take (R52, R56), crewed at [crewClass]. It waits in the yard (R60, R63). That the name
		 * is free in the game, and paying [price], are the player's and the game's to check.
		 */
		/**
		 * A military ship bought through an opportunity (R113): named [name], appearing at once on [system]'s Planet with
		 * [hull] and [modules] and a crew of [crewClass]; refused for a bad name or modules that don't fit.
		 */
		fun militaryOnPlanet(name: String?, system: StarSystem, hull: Hull, modules: List<Module>, crewClass: CrewClass): Ship {
			val trimmed = name?.trim().orEmpty()
			if (trimmed.length !in 1..MAX_NAME_LENGTH) {
				throw InvalidGameOperationException("Choose a name of 1 to $MAX_NAME_LENGTH characters.")
			}
			if (!NAME_PATTERN.matches(trimmed) || trimmed.none { it.isLetterOrDigit() }) {
				throw InvalidGameOperationException(NAME_CHARACTERS_MESSAGE)
			}
			return Ship(trimmed, system, ShipLocation.PLANET, emptyMap(), hull, modules, crewClass, null, 0, emptyList())
		}

		fun commission(name: String?, shipyard: StarSystem, hull: Hull, modules: List<Module>, crewClass: CrewClass, turn: Int): Ship {
			val trimmed = name?.trim().orEmpty()
			if (trimmed.length !in 1..MAX_NAME_LENGTH) {
				throw InvalidInvestmentException("Choose a name of 1 to $MAX_NAME_LENGTH characters.")
			}
			// The name is the ship's address in a URL (`/investment/ships/{ship}`): no "/", "\" or a name of dots alone.
			if (!NAME_PATTERN.matches(trimmed) || trimmed.none { it.isLetterOrDigit() }) {
				throw InvalidInvestmentException(NAME_CHARACTERS_MESSAGE)
			}
			if (!shipyard.hasShipyard()) {
				throw InvalidInvestmentException("${shipyard.displayName} has no shipyard. Choose Beta Hydri, Epsilon Eridani or Tau Ceti.")
			}
			if (!hull.isForSale()) {
				throw InvalidInvestmentException("The ${hull.displayName} is military: it can't be bought at a shipyard.")
			}
			modules.firstOrNull { !it.isForSale() }?.let {
				throw InvalidInvestmentException("${it.displayName} is military: it can't be bought at a shipyard.")
			}
			return Ship(trimmed, shipyard, ShipLocation.SHIPYARD, emptyMap(), hull, modules, crewClass, turn, 0, emptyList())
		}

		/**
		 * The hull and modules of every ship before E07: a Clarinet with 5 Cargo modules, so 10 cargo spaces (R42).
		 * A ship saved without a hull loads as this one (BACKWARD_COMPATIBILITY.md §7).
		 */
		val LEGACY_HULL = Hull.CLARINET
		val LEGACY_MODULES = List(5) { Module.CARGO }

		/**
		 * The hull and modules of a starting ship (R55): a Clarinet with 4 Cargo, 1 Light weapons and 1 Safe jump module,
		 * 8 cargo spaces. Safe jump takes no module capacity, so the hull's 5 are used.
		 */
		val STARTING_HULL = Hull.CLARINET
		val STARTING_MODULES = List(4) { Module.CARGO } + Module.LIGHT_WEAPONS + Module.SAFE_JUMP

		/** A starting ship's crew, already paid (R55, R67); also the class of a ship stored before E09. */
		val STARTING_CREW_CLASS = CrewClass.B

		/** A starting ship carrying nothing (R40, R55: every ship starts empty). */
		fun emptyAt(name: String, system: StarSystem, location: ShipLocation) =
			Ship(name, system, location, emptyMap(), STARTING_HULL, STARTING_MODULES, STARTING_CREW_CLASS, null, 0, emptyList())
	}
}
