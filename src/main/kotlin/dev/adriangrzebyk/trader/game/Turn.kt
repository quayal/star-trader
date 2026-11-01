package dev.adriangrzebyk.trader.game

/**
 * The turn a running game is in and its phase (R41: a game starts in turn 1, Bidding).
 * [doneBidding] lists, in signal order, the players who have signalled done during this turn's Bidding;
 * it's public, so everyone can see who the game is waiting for (D09), and empty outside Bidding.
 */
data class Turn(val number: Int, val phase: TurnPhase, val doneBidding: List<String>) {

	companion object {
		/** The start of turn [number]: Bidding, with nobody done yet. */
		fun bidding(number: Int) = Turn(number, TurnPhase.BIDDING, emptyList())
	}
}
