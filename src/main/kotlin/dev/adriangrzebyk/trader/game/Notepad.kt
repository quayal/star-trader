package dev.adriangrzebyk.trader.game

/**
 * A player's notepad (R135): free text to plan the next turns, private to its owner, whatever they type up to
 * [MAX_LENGTH] characters. It's not part of the game: nothing in the rules reads it.
 */
data class Notepad(val text: String) {

	init {
		if (text.length > MAX_LENGTH) {
			throw InvalidGameOperationException("A notepad holds at most $MAX_LENGTH characters.")
		}
	}

	companion object {
		const val MAX_LENGTH = 5000

		/** What a player who never wrote anything has. */
		val EMPTY = Notepad("")
	}
}
