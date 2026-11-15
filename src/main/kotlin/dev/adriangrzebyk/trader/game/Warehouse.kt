package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's warehouse at [system] (R45): at most one per player per system, and its capacity is [blocks] blocks, each
 * holding as many cargo spaces as a ship (R42). Public, like the board (R50).
 */
data class Warehouse(val system: StarSystem, val blocks: Int) {

	/** The cargo spaces this warehouse holds (R45): [CARGO_SPACES_PER_BLOCK] a block. */
	fun cargoSpaces(): Int = blocks * CARGO_SPACES_PER_BLOCK

	companion object {
		/** R45: one block holds 10 cargo spaces, the same as one ship (R42). */
		const val CARGO_SPACES_PER_BLOCK = Ship.CARGO_SPACES
	}
}
