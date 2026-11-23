package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem

/**
 * A player's warehouse at [system] (R45): at most one per player per system, and its capacity is [blocks] blocks (at
 * least one), each holding as many cargo spaces as a ship (R42). What it holds, [cargo] (units per good), is private to
 * its owner like a ship's (R29); the owner's collected output goes into it (R47). The warehouse itself is public, like
 * the board (R50). It counts as presence for Trading, and loads and unloads like a ship does (R49). [modules] are legal
 * modules stored here (R122), [MODULE_CARGO_SPACES] spaces each, private like the cargo.
 */
data class Warehouse(
	override val system: StarSystem,
	val blocks: Int,
	override val cargo: Map<Goods, Int>,
	val modules: List<Module> = emptyList()
) : CargoHold {

	init {
		require(blocks >= 1) { "A warehouse has at least one block." }
	}

	/** The cargo spaces this warehouse holds (R45): [CARGO_SPACES_PER_BLOCK] a block. */
	fun cargoSpaces(): Int = blocks * CARGO_SPACES_PER_BLOCK

	/** R42: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. A stored module takes [MODULE_CARGO_SPACES] (R122). */
	fun cargoSpacesUsed(): Int = cargo.filterKeys { it.takesCargoSpace }.values.sum() + modules.size * MODULE_CARGO_SPACES

	/** Each different module stored here and how many of it, in the order they were first stored (R122). */
	fun storedModules(): Map<Module, Int> = modules.groupingBy { it }.eachCount()

	/** Whether one more module fits (R122): [MODULE_CARGO_SPACES] free cargo spaces. */
	fun hasRoomForModule(): Boolean = freeCargoSpaces() >= MODULE_CARGO_SPACES

	/** This warehouse with [module] stored (R122), refused for a military one (R112: nothing illegal here) or without room. */
	fun withModuleStored(module: Module): Warehouse {
		if (module.military) {
			throw InvalidInvestmentException("${module.displayName} is military: it can't be stored in a warehouse.")
		}
		if (!hasRoomForModule()) {
			throw InvalidInvestmentException(
				"${cargoName()} has room for no more modules: one takes $MODULE_CARGO_SPACES cargo spaces."
			)
		}
		return copy(modules = modules + module)
	}

	/** This warehouse with one stored [module] taken out to be fitted (R122), refused when it holds none. */
	fun withModuleTaken(module: Module): Warehouse {
		if (module !in modules) {
			throw InvalidInvestmentException("${cargoName()} holds no ${module.displayName} module.")
		}
		return copy(modules = modules - module)
	}

	/** This warehouse with [count] more blocks (R45): what it holds stays. */
	fun addBlocks(count: Int): Warehouse = copy(blocks = blocks + count)

	override fun cargoName(): String = "Your warehouse at ${system.displayName}"

	override fun lots(): List<GoodsLot> =
		cargo.filterValues { it > 0 }.toSortedMap().map { (goods, units) -> GoodsLot(goods, units, system, null) }

	/** How many units of [goods] this warehouse holds. */
	override fun holding(goods: Goods): Int = cargo[goods] ?: 0

	/** Free cargo spaces, never below 0. */
	private fun freeCargoSpaces(): Int = (cargoSpaces() - cargoSpacesUsed()).coerceAtLeast(0)

	/** The free cargo spaces that matter for [goods] (R42): the free space for POLY and ISO, null for CHIPS and FOOD, which take none. */
	fun freeSpacesFor(goods: Goods): Int? = if (goods.takesCargoSpace) freeCargoSpaces() else null

	/** How many of [units] of [goods] fit (R42): POLY and ISO up to the free cargo space, CHIPS and FOOD all. */
	override fun unitsThatFit(goods: Goods, units: Int): Int = freeSpacesFor(goods)?.let { minOf(units, it) } ?: units

	/**
	 * This warehouse after taking in the cargo of [ship], sold here (R66): each good in goods order, as far as it fits
	 * ([load]); the rest is lost. `js/shipyard.js` tells the seller the same before and after.
	 */
	fun storeCargoOf(ship: Ship): Warehouse = Goods.entries.fold(this) { warehouse, goods -> warehouse.load(goods, ship.holding(goods)) }

	/** This warehouse after storing or loading [units] of [goods]: what fits ([unitsThatFit]); the rest is lost (R16, R47, R49). */
	override fun load(goods: Goods, units: Int): Warehouse = withCargo(goods, holding(goods) + unitsThatFit(goods, units))

	/** This warehouse after unloading [units] of [goods] it sold (R10), refused when it holds fewer. */
	override fun unload(goods: Goods, units: Int): Warehouse {
		if (units > holding(goods)) {
			throw InvalidTradeException("Your warehouse at ${system.displayName} has only ${holding(goods)} ${goods.name}.")
		}
		return withCargo(goods, holding(goods) - units)
	}

	private fun withCargo(goods: Goods, units: Int): Warehouse {
		val others = cargo - goods
		return copy(cargo = if (units > 0) others + (goods to units) else others)
	}

	/** This warehouse with no goods in it: what the pirates leave (E12). Stored modules stay (spec 2026-11-23). */
	fun emptied(): Warehouse = copy(cargo = emptyMap())

	/** How many units of goods this warehouse holds, all goods together. */
	fun unitsHeld(): Int = cargo.values.sum()

	companion object {
		/** R45: one block costs 20 HT. */
		const val BLOCK_COST = 20

		/** R45: one block holds 10 cargo spaces, as a ship of the legacy fit did (R42). */
		const val CARGO_SPACES_PER_BLOCK = 10

		/** R122: the cargo spaces one stored module takes. */
		const val MODULE_CARGO_SPACES = 5
	}
}
