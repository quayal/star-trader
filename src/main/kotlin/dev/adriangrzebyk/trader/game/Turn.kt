package dev.adriangrzebyk.trader.game

/**
 * The turn a running game is in and its phase (R41: a game starts in turn 1, Bidding).
 * [doneBidding] lists, in signal order, the players who have signalled done during this turn's Bidding;
 * it's public, so everyone can see who the game is waiting for (D09), and empty outside Bidding.
 * [offers] are this turn's trade offers in the order they were placed. Each is secret to its player (R17), so
 * nothing outside a player's own view may carry them; they stay through Trading and the next turn starts with none.
 */
data class Turn(val number: Int, val phase: TurnPhase, val doneBidding: List<String>, val offers: List<Offer>) {

	/** [email]'s own offers this turn: the only offers a player may ever be shown (R17, A05). */
	fun offersOf(email: String): List<Offer> = offers.filter { it.playerEmail == email }

	companion object {
		/** The start of turn [number]: Bidding, with nobody done and no offers yet. */
		fun bidding(number: Int) = Turn(number, TurnPhase.BIDDING, emptyList(), emptyList())
	}
}
