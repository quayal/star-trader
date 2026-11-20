package dev.adriangrzebyk.trader.game.loan

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem

/**
 * What collecting a loan at the end of its term took (R94): [ships] seized, factory units seized as [factoryUnits], and
 * [writtenOff] HT that nothing was left to cover. [collected] is what the borrower paid in all, for them alone (R29).
 */
data class LoanCollection(val collected: Int, val ships: List<String>, val factoryUnits: List<SeizedUnits>, val writtenOff: Int) {

	/** Whether anything but cash was taken, which is when the public log says so (R94). */
	fun seizedAnything(): Boolean = ships.isNotEmpty() || factoryUnits.isNotEmpty()

	/**
	 * The public log line, without amounts (R94): "Vega Lines's loan fell due: Starfarer and 3 u of the ISO factory at
	 * Tau Ceti seized"; null when cash covered it.
	 */
	fun logText(corporation: String): String? {
		if (!seizedAnything()) {
			return null
		}
		val parts = ships + factoryUnits.map { "${it.units} u of the ${it.goods.name} factory at ${it.system.displayName}" }
		val list = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
		return "$corporation's loan fell due: $list seized"
	}
}

/** [units] of the [goods] factory at [system], seized for a loan (R94). */
data class SeizedUnits(val system: StarSystem, val goods: Goods, val units: Int)
