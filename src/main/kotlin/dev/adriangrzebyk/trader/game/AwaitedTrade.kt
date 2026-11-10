package dev.adriangrzebyk.trader.game

/**
 * The offer the counter has reached, whose player the game waits for (E05-S04-1, D09): the market allows up to
 * [marketMax] units at the counter now, before that player's own money and cargo (R12, R10). Public.
 */
data class AwaitedTrade(val offer: Offer, val marketMax: Int) {

	/**
	 * What took place once its player chose [units], from 0 (trading nothing) to [marketMax], with the [positionRoll] it
	 * made, if any (R43).
	 */
	fun traded(units: Int, positionRoll: PositionRoll?): Trade = Trade(offer, marketMax, units, positionRoll)
}
