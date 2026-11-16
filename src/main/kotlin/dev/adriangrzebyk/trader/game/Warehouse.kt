package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * A player's warehouse at [system] (R45): at most one per player per system, and its capacity is [blocks] blocks (at
 * least one), each holding as many cargo spaces as a ship (R42). What it holds, [cargo] (units per good), is private to
 * its owner like a ship's (R29); the owner's collected output goes into it (R47). The warehouse itself is public, like
 * the board (R50). It counts as presence for Trading, and loads and unloads like a ship does (R49).
 */
data class Warehouse(val system: StarSystem, val blocks: Int, val cargo: Map<Goods, Int>) {

	init {
		require(blocks >= 1) { "A warehouse has at least one block." }
	}

	/** The cargo spaces this warehouse holds (R45): [CARGO_SPACES_PER_BLOCK] a block. */
	fun cargoSpaces(): Int = blocks * CARGO_SPACES_PER_BLOCK

	/** R42: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
	fun cargoSpacesUsed(): Int = cargo.filterKeys { it.takesCargoSpace }.values.sum()

	/** How many units of [goods] this warehouse holds. */
	fun holding(goods: Goods): Int = cargo[goods] ?: 0

	/** Free cargo spaces, never below 0. */
	private fun freeCargoSpaces(): Int = (cargoSpaces() - cargoSpacesUsed()).coerceAtLeast(0)

	/** The free cargo spaces that matter for [goods] (R42): the free space for POLY and ISO, null for CHIPS and FOOD, which take none. */
	fun freeSpacesFor(goods: Goods): Int? = if (goods.takesCargoSpace) freeCargoSpaces() else null

	/** How many of [units] of [goods] fit (R42): POLY and ISO up to the free cargo space, CHIPS and FOOD all. */
	fun unitsThatFit(goods: Goods, units: Int): Int = freeSpacesFor(goods)?.let { minOf(units, it) } ?: units

	/** This warehouse after storing or loading [units] of [goods]: what fits ([unitsThatFit]); the rest is lost (R16, R47, R49). */
	fun load(goods: Goods, units: Int): Warehouse = withCargo(goods, holding(goods) + unitsThatFit(goods, units))

	/** This warehouse after unloading [units] of [goods] it sold (R10), refused when it holds fewer. */
	fun unload(goods: Goods, units: Int): Warehouse {
		if (units > holding(goods)) {
			throw InvalidTradeException("Your warehouse at ${system.displayName} has only ${holding(goods)} ${goods.name}.")
		}
		return withCargo(goods, holding(goods) - units)
	}

	private fun withCargo(goods: Goods, units: Int): Warehouse {
		val others = cargo - goods
		return copy(cargo = if (units > 0) others + (goods to units) else others)
	}

	companion object {
		/** R45: one block holds 10 cargo spaces, the same as one ship (R42). */
		const val CARGO_SPACES_PER_BLOCK = Ship.CARGO_SPACES
	}
}
