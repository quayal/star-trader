package dev.adriangrzebyk.trader.game

/**
 * The turn a running game is in and its phase (R41: a game starts in turn 1, Bidding).
 * [doneBidding] lists, in signal order, the players who have signalled done during this turn's Bidding;
 * it's public, so everyone can see who the game is waiting for (D09), and empty outside Bidding.
 * [offers] are this turn's trade offers in the order they were placed. Each is secret to its player (R17), so
 * nothing outside a player's own view may carry them; they stay through Trading and the next turn starts with none.
 * [bids] are this turn's initiative bids, at most one per player and each secret to its player (R29); like offers
 * they stay for the whole turn. [initiative] is every player's email in initiative order (R27), public, set when
 * Bidding closes and empty before. [turnOrderPicks] are the places players picked in this turn's turn order (R26), in
 * pick order; public, and empty until the first pick. [jumpedShips] names the ships that jumped this turn (each may
 * jump once, R26; names are unique in a game) and [doneJumping] the players whose jump slot has ended, in order; both
 * public and empty until the first jump. [landingShip] is the ship that jumped and whose player hasn't chosen where it
 * ends up yet (R30, S03); public, and null when no ship waits to land.
 */
data class Turn(
	val number: Int,
	val phase: TurnPhase,
	val doneBidding: List<String>,
	val offers: List<Offer>,
	val bids: List<InitiativeBid>,
	val initiative: List<String>,
	val turnOrderPicks: List<TurnOrderPick>,
	val jumpedShips: List<String>,
	val doneJumping: List<String>,
	val landingShip: String?
) {

	/** [email]'s own offers this turn: the only offers a player may ever be shown (R17, A05). */
	fun offersOf(email: String): List<Offer> = offers.filter { it.playerEmail == email }

	/** [email]'s own initiative bid this turn, or null if they haven't placed one: the only bid they may see (R29). */
	fun bidOf(email: String): InitiativeBid? = bids.firstOrNull { it.playerEmail == email }

	/**
	 * The turn order's places, first to last, each holding the email of the player who picked it or null while it's
	 * free. It has a place per player once Bidding has closed (one per initiative entry) and none before.
	 */
	fun turnOrder(): List<String?> = List(initiative.size) { index -> turnOrderPicks.firstOrNull { it.place == index + 1 }?.playerEmail }

	/** Whose pick it is (R26): the first player in initiative order without a place, or null when nobody is picking. */
	fun picker(): String? {
		if (phase != TurnPhase.SHIP_JUMPS) {
			return null
		}
		return initiative.firstOrNull { email -> turnOrderPicks.none { it.playerEmail == email } }
	}

	/**
	 * Whose jump slot it is (R26, S02): once the turn order is set, the first player in it whose slot hasn't ended, or
	 * null while players still pick, after the last slot, and outside Ship-jumps.
	 */
	fun jumper(): String? {
		if (phase != TurnPhase.SHIP_JUMPS || initiative.isEmpty() || picker() != null) {
			return null
		}
		return turnOrder().filterNotNull().firstOrNull { it !in doneJumping }
	}

	companion object {
		/** The start of turn [number]: Bidding, with nobody done, no offers or bids, no initiative, turn order or jumps yet. */
		fun bidding(number: Int) = Turn(
			number,
			TurnPhase.BIDDING,
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			emptyList(),
			null
		)
	}
}
