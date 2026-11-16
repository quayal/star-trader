package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods

class CargoService(private val gameStore: GameStore) {

	/**
	 * [callerEmail] moves [units] of [goods] between their ship [shipName] in port and their warehouse there, into [to],
	 * inside [GameStore.update], so the move is checked against the saved cargo and a double press can't duplicate goods
	 * (A04, R58).
	 */
	fun move(gameId: String, callerEmail: String, shipName: String?, goods: Goods, units: Int, to: CargoDestination): Game =
		gameStore.update(gameId) { it.moveCargo(callerEmail, shipName, goods, units, to) }
}
