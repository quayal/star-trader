package dev.adriangrzebyk.trader.game

class InvestmentService(private val gameStore: GameStore) {

	/**
	 * Records [callerEmail] as done investing inside [GameStore.update], so when the last two players signal at the same
	 * moment Investment closes and the turn ends exactly once, and a signal that arrives after the close is refused
	 * (A04, R44).
	 */
	fun signalDone(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.signalDoneInvesting(callerEmail) }

	/**
	 * [callerEmail] confirms where their factories' output goes inside [GameStore.update], so a double press stores it once
	 * and the placements are checked against the saved ships and warehouses (A04, R47).
	 */
	fun collect(gameId: String, callerEmail: String, placements: List<OutputPlacement>): Game =
		gameStore.update(gameId) { it.collectOutput(callerEmail, placements) }
}
