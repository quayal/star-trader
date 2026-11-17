package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods

class CargoService(private val gameStore: GameStore) {

	/**
	 * [callerEmail] moves [units] of [goods] [from] one of their ships or warehouse [to] another in the same port,
	 * inside [GameStore.update], so the move is checked against the saved cargo and a double press can't duplicate goods
	 * (A04, R58).
	 */
	fun move(gameId: String, callerEmail: String, from: CargoHolder, to: CargoHolder, goods: Goods, units: Int): Game =
		gameStore.update(gameId) { it.moveCargo(callerEmail, from, to, goods, units) }
}
