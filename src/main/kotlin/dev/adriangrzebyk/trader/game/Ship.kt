package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's ship: a [hull] with [modules] (R51, R52). Its name and position are public, like the board; its [cargo]
 * (units per good) is private to its owner (S02-7), as are its hull and modules (R57). Trading fills and empties the
 * cargo (E05). Its stats are derived from the hull and modules, never stored (R53). All its crew are of one [crewClass]
 * (R67). A ship bought in Investment ([boughtOnTurn]; null for every other ship) waits in the shipyard until the next
 * turn's Investment (R63).
 */
data class Ship(
	val name: String,
	val system: StarSystem,
	val location: ShipLocation,
	val cargo: Map<Goods, Int>,
	val hull: Hull,
	val modules: List<Module>,
	val crewClass: CrewClass,
	val boughtOnTurn: Int?
) {

	init {
		hull.checkFits(modules)
	}

	/** R53: the hull's weapons plus every module's. */
	fun weapons(): Int = hull.weapons + modules.sumOf { it.weapons }

	/** R53: the rocket weapons of every module (the hull has none). */
	fun rocketWeapons(): Int = modules.sumOf { it.rocketWeapons }

	/** R53: the crew the hull and every module need. */
	fun crewNeeded(): Int = hull.crew + modules.sumOf { it.crew }

	/** R53: the hull's built-in cargo spaces plus every module's. */
	fun cargoSpaces(): Int = hull.builtInCargo + modules.sumOf { it.cargoSpaces }

	/** R53: the passengers every module takes. */
	fun passengers(): Int = modules.sumOf { it.passengers }

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

	/** R42, R54: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
	fun cargoSpacesUsed(): Int = cargo.filterKeys { it.takesCargoSpace }.values.sum()

	/** Whether this ship is in port, at whatever system: the only place it loads from or unloads into a warehouse (R58). */
	fun isInPort(): Boolean = location == ShipLocation.PORT

	/** Whether this ship is still in the shipyard it was bought at (R60, R64): it doesn't jump, trade or take cargo. */
	fun isInShipyard(): Boolean = location == ShipLocation.SHIPYARD

	/** Whether this ship can be refitted (R65): in the yard, or in Port at a system with a shipyard. */
	fun canBeRefitted(): Boolean = isInShipyard() || (location == ShipLocation.PORT && system.hasShipyard())

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

	/** What selling this ship pays (R66): half its hull's and modules' prices, rounded down; its crew isn't paid back. */
	fun saleValue(): Int = (hull.price + modules.sumOf { it.price }) / 2

	/**
	 * This ship with one more [module] (R65), refused for a military one (R56) or one the hull can't take (R52). Where the
	 * ship is and paying for it are the player's to check.
	 */
	fun withModuleAdded(module: Module): Ship {
		if (!module.isForSale()) {
			throw InvalidInvestmentException("${module.displayName} is military: it can't be bought at a shipyard.")
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
		val refitted = copy(modules = modules - module)
		if (refitted.cargoSpaces() < cargoSpacesUsed()) {
			throw InvalidInvestmentException(
				"$name carries ${cargoSpacesUsed()} units of POLY and ISO; without that module it would have only " +
					"${refitted.cargoSpaces()} cargo spaces."
			)
		}
		return refitted
	}

	/** Whether this ship is in port at [system], where it can trade (R23, R42). */
	fun isInPortAt(system: StarSystem): Boolean = this.system == system && location == ShipLocation.PORT

	/** How many units of [goods] this ship carries. */
	fun holding(goods: Goods): Int = cargo[goods] ?: 0

	/** The free cargo spaces that matter for [goods] (R42): the free space for POLY and ISO, null for CHIPS and FOOD, which take none. */
	fun freeSpacesFor(goods: Goods): Int? = if (goods.takesCargoSpace) freeCargoSpaces() else null

	/** How many of [units] of [goods] fit (R42): POLY and ISO up to the free cargo space, CHIPS and FOOD all. */
	fun unitsThatFit(goods: Goods, units: Int): Int = freeSpacesFor(goods)?.let { minOf(units, it) } ?: units

	/** Free cargo spaces (R42), never below 0, even for a ship that somehow carries more than it holds. */
	private fun freeCargoSpaces(): Int = (cargoSpaces() - cargoSpacesUsed()).coerceAtLeast(0)

	/** This ship after loading [units] of [goods] it bought: what fits ([unitsThatFit]); the rest is lost (R16, R42). */
	fun load(goods: Goods, units: Int): Ship = withCargo(goods, holding(goods) + unitsThatFit(goods, units))

	/** This ship after unloading [units] of [goods] it sold (R10), refused when it holds fewer. */
	fun unload(goods: Goods, units: Int): Ship {
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
	 * This ship's chance to jump to [destination] (R68): the route's base chance plus its crew's modifier and its Safe
	 * jump module's, if it has one. Null for the system it's already at, which is no jump.
	 */
	fun jumpChanceTo(destination: StarSystem): JumpChance? = system.baseJumpChanceTo(destination)?.let { base ->
		JumpChance(base + crewClass.jumpModifier + modules.sumOf { it.jumpModifier })
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
	 * This ship after a jump to [destination] that succeeded (R19, R68, S02): instantly, arriving in Space until its player
	 * chooses where it ends up (S03). Refused as [checkedJumpChanceTo] refuses.
	 */
	fun jumpTo(destination: StarSystem): Ship {
		checkedJumpChanceTo(destination)
		return copy(system = destination, location = ShipLocation.SPACE)
	}

	/** This ship after its player chose where in its system it ends up (R30, S03): never the shipyard (R60). */
	fun landAt(location: ShipLocation): Ship {
		if (!location.isLandable()) {
			throw InvalidLandingException()
		}
		return copy(location = location)
	}

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

		/** R62: letters, digits, spaces, hyphens, apostrophes and full stops, with at least one letter or digit. */
		private val NAME_PATTERN = Regex("[\\p{L}\\p{N} '.-]+")
		const val NAME_CHARACTERS_MESSAGE = "Use letters, digits, spaces, hyphens, apostrophes and full stops."

		/**
		 * A new, empty ship called [name] (trimmed), bought in turn [turn] at [shipyard] (R61): a civilian [hull] with
		 * civilian [modules] it can take (R52, R56), crewed at [crewClass]. It waits in the yard (R60, R63). That the name
		 * is free in the game, and paying [price], are the player's and the game's to check.
		 */
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
			return Ship(trimmed, shipyard, ShipLocation.SHIPYARD, emptyMap(), hull, modules, crewClass, turn)
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
			Ship(name, system, location, emptyMap(), STARTING_HULL, STARTING_MODULES, STARTING_CREW_CLASS, null)
	}
}
