package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem

class ShipJumpsService(private val gameStore: GameStore) {

	/**
	 * [callerEmail] picks [place] in this turn's turn order inside [GameStore.update], so two players acting at once
	 * can't both take a place or pick out of turn: the second is checked against the first's saved pick (A04, R26).
	 */
	fun pickPlace(gameId: String, callerEmail: String, place: Int?): Game =
		gameStore.update(gameId) { it.pickTurnOrderPlace(callerEmail, place) }

	/**
	 * [callerEmail] jumps [shipName] to [destination] inside [GameStore.update], so a ship can't jump twice and a jump
	 * can't land out of turn when two requests race: the second is checked against the first's saved jump (A04, R26).
	 */
	fun jump(gameId: String, callerEmail: String, shipName: String?, destination: StarSystem?): Game =
		gameStore.update(gameId) { it.jumpShip(callerEmail, shipName, destination) }

	/** [callerEmail] ends their jump slot inside [GameStore.update], so it ends once (A04, S02-6). */
	fun finishJumping(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.finishJumping(callerEmail) }

	/** [callerEmail] chooses where the ship that just jumped ends up, inside [GameStore.update], so it lands once (A04, R30). */
	fun land(gameId: String, callerEmail: String, location: ShipLocation?): Game =
		gameStore.update(gameId) { it.landShip(callerEmail, location) }
}
