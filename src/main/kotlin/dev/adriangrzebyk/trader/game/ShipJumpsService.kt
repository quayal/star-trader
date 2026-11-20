package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.battle.BattleOrder

class ShipJumpsService(private val gameStore: GameStore, private val dice: Dice) {

	/**
	 * [callerEmail] picks [place] in this turn's turn order inside [GameStore.update], so two players acting at once
	 * can't both take a place or pick out of turn: the second is checked against the first's saved pick (A04, R26).
	 */
	fun pickPlace(gameId: String, callerEmail: String, place: Int?): Game =
		gameStore.update(gameId) { it.pickTurnOrderPlace(callerEmail, place, dice) }

	/**
	 * [callerEmail] jumps [shipName] to [destination] inside [GameStore.update], so a ship can't jump twice and a jump
	 * can't land out of turn when two requests race: the second is checked against the first's saved jump (A04, R26).
	 * [dice] rolls whether it succeeds (R68).
	 */
	fun jump(gameId: String, callerEmail: String, shipName: String?, destination: StarSystem?): Game =
		gameStore.update(gameId) { it.jumpShip(callerEmail, shipName, destination, dice) }

	/** [callerEmail] ends their jump slot inside [GameStore.update], so it ends once (A04, S02-6). */
	fun finishJumping(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.finishJumping(callerEmail) }

	/** [callerEmail] chooses where the ship that just jumped ends up, inside [GameStore.update], so it lands once (A04, R30). */
	fun land(gameId: String, callerEmail: String, location: ShipLocation?): Game =
		gameStore.update(gameId) { it.landShip(callerEmail, location) }

	/**
	 * [callerEmail] decides which one of their ships, [ship], tries to intercept the ship that just arrived (R70), or none,
	 * inside [GameStore.update], so a decision counts once and the last one rolls the tries once (A04).
	 */
	fun intercept(gameId: String, callerEmail: String, ship: String?): Game =
		gameStore.update(gameId) { it.answerInterception(callerEmail, ship, dice) }

	/** [callerEmail] gives this round's [orders] (R74) inside [GameStore.update], so the last one fires the round once (A04). */
	fun giveOrders(gameId: String, callerEmail: String, orders: List<BattleOrder>): Game =
		gameStore.update(gameId) { it.giveBattleOrders(callerEmail, orders, dice) }

	/** [callerEmail] places their ships' hits (R76) inside [GameStore.update], so they're applied once (A04). */
	fun placeHits(gameId: String, callerEmail: String, placements: Map<String, HitPlacement>): Game =
		gameStore.update(gameId) { it.placeBattleHits(callerEmail, placements) }

	/** [callerEmail] places their surviving ships after the battle (R77) inside [GameStore.update], so once (A04). */
	fun landAfterBattle(gameId: String, callerEmail: String, locations: Map<String, ShipLocation>): Game =
		gameStore.update(gameId) { it.landAfterBattle(callerEmail, locations) }
}
