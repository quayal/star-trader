package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.game.CrewClass
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.Module

/**
 * What a player asks for when taking an opportunity (R111–R114), only the parts its kind needs: the [ship] that buys,
 * carries or leaves; how many [units]; or a new ship's [name], [hull], [modules] and [crewClass]. For modules alone,
 * [removed] are the ship's modules that come off to make room for them (creator, 2026-11-21).
 */
data class TakeOrder(
	val ship: String? = null,
	val units: Int? = null,
	val name: String? = null,
	val hull: Hull? = null,
	val modules: List<Module> = emptyList(),
	val crewClass: CrewClass? = null,
	val removed: List<Module> = emptyList()
)
