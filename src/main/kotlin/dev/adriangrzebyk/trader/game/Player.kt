package dev.adriangrzebyk.trader.game

/**
 * A player's membership in one game; [email] is the membership key. [money] is private to its owner (R29);
 * [marketPositions] are public (R28), and a player holds none until they earn one (R40, R43).
 */
data class Player(
	val email: String,
	val name: String,
	val corporationName: String,
	val money: Int,
	val ships: List<Ship>,
	val marketPositions: List<MarketPosition>
) {

	companion object {
		/** A player in the lobby: money and ships arrive when the game starts (R40). */
		fun newcomer(email: String, name: String, corporationName: String) = Player(email, name, corporationName, 0, emptyList(), emptyList())
	}
}
