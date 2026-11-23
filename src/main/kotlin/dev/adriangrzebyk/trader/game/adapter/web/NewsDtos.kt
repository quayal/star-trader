package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.CrewClass
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.TakeOrder
import java.math.BigDecimal

/**
 * Taking an opportunity (R111–R114): `{ ship, units, name, hull, modules, crewClass, remove }`, the parts its kind needs
 * (`remove`: modules taken off to make room for military ones, creator 2026-11-21). Names
 * are checked here: an unknown one reads as missing, so the domain refuses it with its own message.
 */
data class TakeOpportunityRequestDto(
	val ship: String? = null,
	val units: BigDecimal? = null,
	val name: String? = null,
	val hull: String? = null,
	val modules: List<String>? = null,
	val crewClass: String? = null,
	val remove: List<String>? = null
) {

	fun order(): TakeOrder = TakeOrder(
		ship,
		units?.let { runCatching { it.intValueExact() }.getOrNull() },
		name,
		Hull.entries.firstOrNull { it.name == hull },
		modules.orEmpty().mapNotNull { id -> Module.entries.firstOrNull { it.name == id } },
		CrewClass.entries.firstOrNull { it.name == crewClass },
		remove.orEmpty().mapNotNull { id -> Module.entries.firstOrNull { it.name == id } }
	)
}

/** Taking passengers (R125): `{ ship, system, groups }`, the destination system and how many groups. */
data class PassengerRequestDto(val ship: String? = null, val system: String? = null, val groups: Int? = null) {

	fun system(): StarSystem? = StarSystem.entries.firstOrNull { it.name == system }
}

/** Bringing an expedition back (R114) or selling special goods (R112): `{ ship, system, goods }`. */
data class SpecialSaleRequestDto(val ship: String? = null, val system: String? = null, val goods: String? = null) {

	fun system(): StarSystem? = StarSystem.entries.firstOrNull { it.name == system }

	fun goods(): SpecialGoods? = SpecialGoods.entries.firstOrNull { it.name == goods }
}
