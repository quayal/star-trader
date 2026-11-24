package dev.adriangrzebyk.trader.game

class NotepadService(private val gameStore: GameStore, private val notepads: NotepadStore) {

	/** The caller's notepad in game [gameId], empty when they never wrote one; anyone but a player of the game is refused (A05, §10). */
	fun read(gameId: String, callerEmail: String): Notepad {
		val game = gameStore.find(gameId) ?: throw GameNotFoundException()
		if (!game.hasPlayer(callerEmail)) {
			throw NotAGameMemberException()
		}
		return notepads.find(gameId, callerEmail) ?: Notepad.EMPTY
	}

	/** Saves [text] as the caller's notepad in game [gameId]: players still in the game only, so an eliminated player keeps theirs to read (R135). */
	fun write(gameId: String, callerEmail: String, text: String?): Notepad {
		val game = gameStore.find(gameId) ?: throw GameNotFoundException()
		if (!game.hasPlayer(callerEmail)) {
			throw NotAGameMemberException()
		}
		if (!game.isActivePlayer(callerEmail)) {
			throw InvalidGameOperationException("You are out of the game; your notes are read-only.")
		}
		val notepad = Notepad(text.orEmpty())
		notepads.save(gameId, callerEmail, notepad)
		return notepad
	}
}
