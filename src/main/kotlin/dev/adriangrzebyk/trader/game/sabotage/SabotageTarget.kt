package dev.adriangrzebyk.trader.game.sabotage

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.CorporationNames

/** What a sabotage is aimed at (R97): another player's ([ownerEmail]) ship in Port, warehouse or factory at [system]. */
sealed interface SabotageTarget {
	val ownerEmail: String
	val system: StarSystem

	/** The target for the public log: "Orion Freight's Starfarer", "Orion Freight's warehouse", "Orion Freight's CHIPS factory". */
	fun describe(names: CorporationNames): String

	/** Its kind, as the screens and requests name it: `SHIP`, `WAREHOUSE` or `FACTORY`. */
	fun kind(): String = when (this) {
		is Ship -> "SHIP"
		is Warehouse -> "WAREHOUSE"
		is Factory -> "FACTORY"
	}

	/** The public log line of an [effect] above 0 on this target (R101). */
	fun effectLog(names: CorporationNames, effect: Int): String

	/** The ship [name] in Port at [system]. */
	data class Ship(override val ownerEmail: String, override val system: StarSystem, val name: String) : SabotageTarget {
		override fun describe(names: CorporationNames): String = "${names.of(ownerEmail)}'s $name"

		override fun effectLog(names: CorporationNames, effect: Int): String =
			"Sabotage at ${system.displayName}: ${describe(names)} took $effect ${if (effect == 1) "hit" else "hits"}"
	}

	/** The warehouse at [system]. */
	data class Warehouse(override val ownerEmail: String, override val system: StarSystem) : SabotageTarget {
		override fun describe(names: CorporationNames): String = "${names.of(ownerEmail)}'s warehouse"

		override fun effectLog(names: CorporationNames, effect: Int): String =
			"Sabotage at ${system.displayName}: ${describe(names)} lost $effect u"
	}

	/** The [goods] factory at [system]. */
	data class Factory(override val ownerEmail: String, override val system: StarSystem, val goods: Goods) : SabotageTarget {
		override fun describe(names: CorporationNames): String = "${names.of(ownerEmail)}'s ${goods.name} factory"

		override fun effectLog(names: CorporationNames, effect: Int): String =
			"Sabotage at ${system.displayName}: ${describe(names)} disabled for $effect ${if (effect == 1) "turn" else "turns"}"
	}
}
