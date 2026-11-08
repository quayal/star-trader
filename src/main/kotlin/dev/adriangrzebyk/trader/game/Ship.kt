package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's ship. Its name and position are public, like the board; its [cargo] (units per good) is private
 * to its owner (S02-7). Trading fills and empties the cargo (E05).
 */
data class Ship(val name: String, val system: StarSystem, val location: ShipLocation, val cargo: Map<Goods, Int>) {

	/** R42: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
	fun cargoSpacesUsed(): Int = cargo.filterKeys { it.takesCargoSpace }.values.sum()

	/** How many units of [goods] this ship carries. */
	fun holding(goods: Goods): Int = cargo[goods] ?: 0

	/** How many of [units] of [goods] fit (R42): POLY and ISO up to the free cargo space, CHIPS and FOOD all. */
	fun unitsThatFit(goods: Goods, units: Int): Int = if (goods.takesCargoSpace) minOf(units, CARGO_SPACES - cargoSpacesUsed()) else units

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
		/** R42: every ship has 10 cargo spaces. */
		const val CARGO_SPACES = 10

		/** Two names per player in join order; enough for a full game of 6. */
		val NAMES = listOf(
			"Wayfarer", "Lantern", "Meridian", "Halcyon", "Bright Reach", "Corvid",
			"Ironside", "Tallow", "Ostrava", "Kestrel", "Solace", "Pilgrim"
		)

		/** A ship carrying nothing (R40: every ship starts empty). */
		fun emptyAt(name: String, system: StarSystem, location: ShipLocation) = Ship(name, system, location, emptyMap())
	}
}
