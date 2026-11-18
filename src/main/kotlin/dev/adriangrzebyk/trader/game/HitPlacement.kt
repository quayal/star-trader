package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods

/**
 * Where a ship's owner puts the hits it took in a battle round (R76): [hullHits] on the hull, each of [moduleHits] on one
 * fitted module, and [jettison], the POLY and ISO thrown overboard when the damage leaves too little cargo space.
 */
data class HitPlacement(val hullHits: Int, val moduleHits: List<ModuleHit>, val jettison: Map<Goods, Int>) {

	/** How many hits this places. */
	fun total(): Int = hullHits + moduleHits.sumOf { it.hits }
}

/** Hits on one fitted [module], working or [alreadyDamaged] (R76): 2 destroy a working one, 1 damages it; 1 destroys a damaged one. */
data class ModuleHit(val module: Module, val alreadyDamaged: Boolean, val hits: Int)
