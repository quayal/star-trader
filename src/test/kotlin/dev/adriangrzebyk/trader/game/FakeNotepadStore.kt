package dev.adriangrzebyk.trader.game

class FakeNotepadStore : NotepadStore {

	private val notepads = mutableMapOf<Pair<String, String>, Notepad>()

	override fun find(gameId: String, email: String): Notepad? = notepads[gameId to email]

	override fun save(gameId: String, email: String, notepad: Notepad) {
		notepads[gameId to email] = notepad
	}
}
