package dev.adriangrzebyk.trader.game

class ShipJumpsService(private val gameStore: GameStore) {

	/**
	 * [callerEmail] picks [place] in this turn's turn order inside [GameStore.update], so two players acting at once
	 * can't both take a place or pick out of turn: the second is checked against the first's saved pick (A04, R26).
	 */
	fun pickPlace(gameId: String, callerEmail: String, place: Int?): Game {
		// Games are never deleted, so one found here is still there for the update.
		gameStore.find(gameId) ?: throw GameNotFoundException()
		return gameStore.update(gameId) { it.pickTurnOrderPlace(callerEmail, place) }
	}
}
