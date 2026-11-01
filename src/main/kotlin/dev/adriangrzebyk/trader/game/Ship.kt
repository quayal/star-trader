package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem

/** A player's ship; its name and position are public, like the board. Cargo arrives with trading (E05). */
data class Ship(val name: String, val system: StarSystem, val location: ShipLocation) {

	companion object {
		/** R42: every ship has 10 cargo spaces. */
		const val CARGO_SPACES = 10

		/** Two names per player in join order; enough for a full game of 6. */
		val NAMES = listOf(
			"Wayfarer", "Lantern", "Meridian", "Halcyon", "Bright Reach", "Corvid",
			"Ironside", "Tallow", "Ostrava", "Kestrel", "Solace", "Pilgrim"
		)
	}
}
