package dev.adriangrzebyk.trader.game

/**
 * The turn a running game is in and its phase (R41: a game starts in turn 1, Bidding).
 * [doneBidding] lists, in signal order, the players who have signalled done during this turn's Bidding;
 * it's public, so everyone can see who the game is waiting for (D09), and empty outside Bidding.
 * [offers] are this turn's trade offers in the order they were placed. Each is secret to its player (R17), so
 * nothing outside a player's own view may carry them; they stay through Trading and the next turn starts with none.
 * [bids] are this turn's initiative bids, at most one per player and each secret to its player (R29); like offers
 * they stay for the whole turn. [initiative] is every player's email in initiative order (R27), public, set when
 * Bidding closes and empty before.
 */
data class Turn(
	val number: Int,
	val phase: TurnPhase,
	val doneBidding: List<String>,
	val offers: List<Offer>,
	val bids: List<InitiativeBid>,
	val initiative: List<String>
) {

	/** [email]'s own offers this turn: the only offers a player may ever be shown (R17, A05). */
	fun offersOf(email: String): List<Offer> = offers.filter { it.playerEmail == email }

	/** [email]'s own initiative bid this turn, or null if they haven't placed one: the only bid they may see (R29). */
	fun bidOf(email: String): InitiativeBid? = bids.firstOrNull { it.playerEmail == email }

	companion object {
		/** The start of turn [number]: Bidding, with nobody done, no offers or bids, and no initiative order yet. */
		fun bidding(number: Int) = Turn(number, TurnPhase.BIDDING, emptyList(), emptyList(), emptyList(), emptyList())
	}
}
