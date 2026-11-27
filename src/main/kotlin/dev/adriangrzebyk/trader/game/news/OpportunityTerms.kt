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

	/**
	 * Everything a player needs to decide whether to take it (creator, 2026-11-25): where, what it needs, what it costs or pays,
	 * what each unit takes aboard, and whether taking it is announced. One sentence each.
	 */
	fun facts(): List<String>

	/** The special goods it sells (R112), or null for one that sells none; they say what each unit takes aboard. */
	fun specialGoods(): SpecialGoods? = null

	/** The most units one taker may buy (R112), or null for one that sells no goods. */
	fun maxUnits(): Int? = specialGoods()?.let { MAX_UNITS }

	/** R113: the military modules it sells, each kind at most once (creator, 2026-11-26), or none. */
	fun modulesOnOffer(): List<Module> = emptyList()

	/** R113: the most of [modulesOnOffer] one taker may buy. */
	fun moduleLimit(): Int = modulesOnOffer().size

	/** R113: the system on whose Planet modules bought alone are fitted to the buyer's ship, or null for one that sells none. */
	fun modulesAt(): StarSystem? = null

	/**
	 * R113 (creator, 2026-11-26): whether [modules] alone are a purchase it allows, from one module to everything on offer, each
	 * kind once.
	 */
	fun acceptsModulesAlone(modules: List<Module>): Boolean = modules.isNotEmpty() &&
		modules.size <= moduleLimit() &&
		modules.all { it in modulesOnOffer() } &&
		modules.distinct().size == modules.size

	/** R112: up to [MAX_UNITS] of illegal [goods] at [price] HT each, bought on [buyAt]'s Planet, sold on [sellAt]'s Planet. */
	data class IllegalGoods(val goods: SpecialGoods, val price: Int, val buyAt: StarSystem, val sellAt: StarSystem) : OpportunityTerms {
		override val legal = false

		override fun specialGoods(): SpecialGoods = goods

		override fun description(): String =
			"buy ${goods.displayName} on ${buyAt.displayName}'s Planet ($price HT) for sale on ${sellAt.displayName}'s Planet"

		override fun facts(): List<String> = listOf(
			"Buy 1 to $MAX_UNITS units of ${goods.displayName} at $price HT each, with a ship of yours on ${buyAt.displayName}'s Planet.",
			goods.holdText(),
			// Creator, 2026-11-26: the price table is the same for every lot, so it lives in the Guide, not in each tip-off.
			"Sell them on ${sellAt.displayName}'s Planet, on the black market, in a later Opportunity: every unit aboard at once, " +
				"at a d6 roll on the black market table in the Guide.",
			SECRET
		)
	}

	/** R112: up to [MAX_UNITS] furs at [price] in [buyAt]'s Port, sold for [salePrice] each in [sellAt]'s Port. */
	data class Furs(val price: Int, val buyAt: StarSystem, val salePrice: Int, val sellAt: StarSystem) : OpportunityTerms {
		override val legal = true

		override fun specialGoods(): SpecialGoods = SpecialGoods.FURS

		override fun description(): String =
			"buy furs in ${buyAt.displayName}'s Port ($price HT) for sale in ${sellAt.displayName}'s Port ($salePrice HT)"

		override fun facts(): List<String> = listOf(
			"Buy 1 to $MAX_UNITS units of furs at $price HT each, with a ship of yours in ${buyAt.displayName}'s Port.",
			SpecialGoods.FURS.holdText(),
			"Sell them for $salePrice HT each in ${sellAt.displayName}'s Port, on a later Opportunity turn there.",
			ANNOUNCED
		)
	}

	/**
	 * R113: a military hull from [hulls] and/or up to [maxModules] military modules, one of each kind, on [at]'s Planet. The
	 * buyer takes anything from one item to all of it (creator, 2026-11-26): with no hull, the modules go to their ship there.
	 */
	data class MilitaryShip(val hulls: List<Hull>, val maxModules: Int, val at: StarSystem) : OpportunityTerms {
		override val legal = false

		override fun modulesOnOffer(): List<Module> = Module.military()

		override fun moduleLimit(): Int = minOf(maxModules, modulesOnOffer().size)

		override fun modulesAt(): StarSystem = at

		/** Whether [modules] may come with the hull: its military ones within [moduleLimit], each kind once. */
		fun acceptsWithHull(modules: List<Module>): Boolean {
			val military = modules.filter { it.military }
			return military.size <= moduleLimit() && military.distinct().size == military.size
		}

		override fun description(): String = "buy ${hullText()} and/or ${modulesText()}, on ${at.displayName}'s Planet"

		override fun facts(): List<String> = listOf(
			"Buy ${hullText()} and/or ${modulesText()}: anything from one of them to all of it.",
			if (hulls.size == 1) {
				"The ${hulls.single().displayName} hull (${hulls.single().price} HT plus crew) appears on ${at.displayName}'s Planet: " +
					"no ship of yours is needed."
			} else {
				"Hulls: ${hullPrices(hulls)}, plus crew. It appears on ${at.displayName}'s Planet: no ship of yours is needed."
			},
			"Modules, each plus its crew: ${modulePrices(modulesOnOffer())}. They're fitted to the new hull, or without one to a ship " +
				"of yours on ${at.displayName}'s Planet that has room.",
			MILITARY,
			SECRET
		)

		/** "a Spear hull" or "a military hull (Dagger, Sword or Spear)". */
		private fun hullText(): String = if (hulls.size == 1) {
			"a ${hulls.single().displayName} hull"
		} else {
			"a military hull (${hulls.dropLast(1).joinToString(", ") { it.displayName }} or ${hulls.last().displayName})"
		}

		/** "1 military module" or "up to 4 military modules, one of each kind". */
		private fun modulesText(): String =
			if (moduleLimit() == 1) "1 military module" else "up to ${moduleLimit()} military modules, one of each kind"
	}

	/** R113: military modules for a ship of the buyer's on [at]'s Planet: [modules], each at most once. */
	data class MilitaryModules(val modules: List<Module>, val at: StarSystem) : OpportunityTerms {
		override val legal = false

		override fun modulesOnOffer(): List<Module> = modules

		override fun modulesAt(): StarSystem = at

		override fun description(): String = if (modules.size == 1) {
			"buy a ${modules.single().displayName} module on ${at.displayName}'s Planet"
		} else {
			"buy one of each military module on ${at.displayName}'s Planet"
		}

		override fun facts(): List<String> = listOf(
			if (modules.size == 1) {
				"Fit a ${modules.single().displayName} module (${modules.single().price} HT plus its crew) to a ship of yours on ${at.displayName}'s Planet."
			} else {
				"Fit any of these to a ship of yours on ${at.displayName}'s Planet, each at most once, plus its crew: ${modulePrices(modules)}."
			},
			"Short of room? Take modules off that ship in the same purchase, for half their price back.",
			MILITARY,
			SECRET
		)
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

		override fun facts(): List<String> = listOfNotNull(
			"Pick up $cargo in ${from.displayName}'s Port and carry them to ${to.displayName}'s Port.",
			"Needs a ship of yours in ${from.displayName}'s Port with one whole ${space.displayName} module free; it stays taken until you deliver.",
			"Paid $payment HT${if (reputation > 0) " and +$reputation reputation" else ""} when you deliver in ${to.displayName}'s Port " +
				"on your Opportunity turn. No deadline, but the job is lost with the ship.",
			"Needs reputation $minReputation or more to accept.".takeIf { minReputation > 0 },
			ANNOUNCED
		)
	}

	/** R114: send one legal ship on a scientific expedition. */
	data object Expedition : OpportunityTerms {
		override val legal = true

		override fun description(): String = "send a ship on a scientific expedition"

		override fun facts(): List<String> = listOf(
			"Send one ship of yours with no military parts off the board, cargo and all.",
			"A public d6: on 1 the ship is lost at once; on any other roll it returns that many turns later, in that turn's " +
				"Opportunity, to the Port you choose, and you're paid 2d6 × 50 HT.",
			ANNOUNCED
		)
	}

	companion object {
		/** R112: the most units an illegal-goods or furs opportunity sells. */
		const val MAX_UNITS = 10

		/** R111: whether taking it is told to the other players. */
		private const val SECRET = "Illegal: nobody is told you took it."
		private const val ANNOUNCED = "Legal: everyone is told you took it."

		/** R56: what owning military parts means. */
		private const val MILITARY =
			"Military parts are illegal to possess: a ship with any can't use the Port of a system with legal system 3 or 4."
	}
}

/** "Dagger 250 HT, Sword 500 HT, Spear 600 HT": the hulls on offer with their catalogue prices (R113). */
private fun hullPrices(hulls: List<Hull>): String = hulls.joinToString(", ") { "${it.displayName} ${it.price} HT" }

/** "Arsenal 100 HT, Battle comm 50 HT": the modules on offer with their catalogue prices (R113). */
private fun modulePrices(modules: List<Module>): String = modules.joinToString(", ") { "${it.displayName} ${it.price} HT" }
