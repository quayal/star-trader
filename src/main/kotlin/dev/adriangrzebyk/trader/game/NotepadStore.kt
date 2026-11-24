package dev.adriangrzebyk.trader.game

/** Where players' notepads are kept (R135), one per player per game, apart from the game itself. */
interface NotepadStore {

	/** The notepad of the player with [email] in game [gameId], or null when they never saved one. */
	fun find(gameId: String, email: String): Notepad?

	/** Saves [notepad] as that player's, replacing the last one. */
	fun save(gameId: String, email: String, notepad: Notepad)
}
