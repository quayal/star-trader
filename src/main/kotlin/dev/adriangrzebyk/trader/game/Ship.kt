package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's ship: a [hull] with [modules] (R51, R52). Its name and position are public, like the board; its [cargo]
 * (units per good) is private to its owner (S02-7), as are its hull and modules (R57). Trading fills and empties the
 * cargo (E05). Its stats are derived from the hull and modules, never stored (R53).
 */
data class Ship(
	val name: String,
	val system: StarSystem,
	val location: ShipLocation,
	val cargo: Map<Goods, Int>,
	val hull: Hull,
	val modules: List<Module>
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

	/** R42, R54: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
	fun cargoSpacesUsed(): Int = cargo.filterKeys { it.takesCargoSpace }.values.sum()

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
	 * This ship after jumping to [destination] (R19, S02): instantly, arriving in Space until its player chooses where it
	 * ends up (S03). Never to the system it's already at (S02-4).
	 */
	fun jumpTo(destination: StarSystem): Ship {
		if (destination == system) {
			throw InvalidJumpException("$name is already at ${destination.displayName}. Pick another destination.")
		}
		return copy(system = destination, location = ShipLocation.SPACE)
	}

	/** This ship after its player chose where in its system it ends up (R30, S03). */
	fun landAt(location: ShipLocation): Ship = copy(location = location)

	/** Where this ship is, for the public log: "Wayfarer at Tau Ceti". */
	fun position(): String = "$name at ${system.displayName}"

	/** The public log's account of the jump that brought this ship here (S02-7): "Wayfarer → Tau Ceti". */
	fun jumpLog(): String = "$name → ${system.displayName}"

	/** The public log's account of where this ship ended up (S03-3): "Wayfarer lands at the port (Tau Ceti)". */
	fun landingLog(): String = "$name ${location.landingLog} (${system.displayName})"

	companion object {
		/** Two names per player in join order; enough for a full game of 6. */
		val NAMES = listOf(
			"Wayfarer", "Lantern", "Meridian", "Halcyon", "Bright Reach", "Corvid",
			"Ironside", "Tallow", "Ostrava", "Kestrel", "Solace", "Pilgrim"
		)

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

		/** A starting ship carrying nothing (R40, R55: every ship starts empty). */
		fun emptyAt(name: String, system: StarSystem, location: ShipLocation) =
			Ship(name, system, location, emptyMap(), STARTING_HULL, STARTING_MODULES)
	}
}
