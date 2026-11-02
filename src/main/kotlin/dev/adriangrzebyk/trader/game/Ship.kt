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
