package dev.adriangrzebyk.trader.board

/**
 * R31: a system's three locations and their statistics — the Port's class and legal system, Space's police and
 * the Planet's security, corrected by the creator on 2026-11-20 (R31). Planet security decides landings (R116), and the
 * legal system where military modules may change ships (R123); police has no mechanic yet.
 */
data class LocationStats(val portClass: Int, val legalSystem: Int, val police: Int, val security: Int) {

	/** Whether the Port admits a ship with military parts (R56): legal system 1 or 2; there military modules change ships (R123). */
	fun admitsMilitaryShips(): Boolean = legalSystem <= MAX_LEGAL_SYSTEM_FOR_MILITARY

	private companion object {
		/** R56: a ship with any military part can't enter a Port with legal system 3 or 4. */
		const val MAX_LEGAL_SYSTEM_FOR_MILITARY = 2
	}
}
