package dev.adriangrzebyk.trader.game

/**
 * The Parking Hangar question a jumping player has to answer before anything else in their slot (R127): whether each of
 * [ships] (names, in the order they're asked) stays or goes into the hangar for 5 HT. [endsSlot] says it was raised by the
 * slot ending, so the last answer ends it; a question raised by a landing leaves the slot going on.
 */
data class HangarAsk(val ships: List<String>, val endsSlot: Boolean) {

	init {
		require(ships.isNotEmpty()) { "A hangar question needs a ship." }
	}

	/** The ship being asked about now. */
	fun ship(): String = ships.first()

	/** What's left to ask once the current ship is answered, or null when that was the last. */
	fun answered(): HangarAsk? = ships.drop(1).takeIf { it.isNotEmpty() }?.let { copy(ships = it) }
}
