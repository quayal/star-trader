package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.Module

/**
 * What an opportunity offers (R111–R114), from the creator's table. [description] completes "the opportunity to …"
 * (creator's wording, 2026-11-20: tip-offs are learned, opportunities taken). A [legal] one is announced with its taker
 * when taken; an illegal one stays secret (R111).
 */
sealed interface OpportunityTerms {
	val legal: Boolean

	fun description(): String

	/** The special goods it sells (R112), or null for one that sells none; they say what each unit takes aboard. */
	fun specialGoods(): SpecialGoods? = null

	/** The most units one taker may buy (R112), or null for one that sells no goods. */
	fun maxUnits(): Int? = specialGoods()?.let { MAX_UNITS }

	/** R112: up to [MAX_UNITS] of illegal [goods] at [price] HT each, bought on [buyAt]'s Planet, sold on [sellAt]'s Planet. */
	data class IllegalGoods(val goods: SpecialGoods, val price: Int, val buyAt: StarSystem, val sellAt: StarSystem) : OpportunityTerms {
		override val legal = false

		override fun specialGoods(): SpecialGoods = goods

		override fun description(): String =
			"buy ${goods.displayName} on ${buyAt.displayName}'s Planet ($price HT) for sale on ${sellAt.displayName}'s Planet"
	}

	/** R112: up to [MAX_UNITS] furs at [price] in [buyAt]'s Port, sold for [salePrice] each in [sellAt]'s Port. */
	data class Furs(val price: Int, val buyAt: StarSystem, val salePrice: Int, val sellAt: StarSystem) : OpportunityTerms {
		override val legal = true

		override fun specialGoods(): SpecialGoods = SpecialGoods.FURS

		override fun description(): String =
			"buy furs in ${buyAt.displayName}'s Port ($price HT) for sale in ${sellAt.displayName}'s Port ($salePrice HT)"
	}

	/**
	 * R113: a military hull from [hulls] (optional when [hullOptional]) with up to [maxModules] military modules, appearing
	 * on [at]'s Planet; with no hull, the modules go to the buyer's ship there.
	 */
	data class MilitaryShip(val hulls: List<Hull>, val maxModules: Int, val at: StarSystem, val hullOptional: Boolean) : OpportunityTerms {
		override val legal = false

		override fun description(): String {
			val hull = if (hulls.size == 1) "a ${hulls.single().displayName} hull" else "a military hull"
			val modules = if (maxModules == 1) "a military module" else "up to $maxModules military modules"
			return "buy $hull ${if (hullOptional) "and/or" else "with"} $modules on ${at.displayName}'s Planet"
		}
	}

	/** R113: military modules for a ship of the buyer's on [at]'s Planet: [modules], each at most once. */
	data class MilitaryModules(val modules: List<Module>, val at: StarSystem) : OpportunityTerms {
		override val legal = false

		override fun description(): String = if (modules.size == 1) {
			"buy a ${modules.single().displayName} module on ${at.displayName}'s Planet"
		} else {
			"buy one of each military module on ${at.displayName}'s Planet"
		}
	}

	/**
	 * R114: carry [cargo] from [from]'s Port to [to]'s Port in one whole [space] module, paid [payment] HT and [reputation]
	 * once delivered there in the Opportunity phase (R121); [minReputation] to accept.
	 */
	data class Delivery(
		val cargo: String,
		val from: StarSystem,
		val to: StarSystem,
		val space: Module,
		val minReputation: Int,
		val payment: Int,
		val reputation: Int
	) : OpportunityTerms {
		override val legal = true

		override fun description(): String = "transport $cargo from ${from.displayName} to ${to.displayName} ($payment HT)"
	}

	/** R114: send one legal ship on a scientific expedition. */
	data object Expedition : OpportunityTerms {
		override val legal = true

		override fun description(): String = "send a ship on a scientific expedition"
	}

	companion object {
		/** R112: the most units an illegal-goods or furs opportunity sells. */
		const val MAX_UNITS = 10
	}
}
