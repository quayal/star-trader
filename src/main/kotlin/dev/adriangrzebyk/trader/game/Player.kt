package dev.adriangrzebyk.trader.game

/** A player's membership in one game; [email] is the membership key. [money] is private to its owner (R29). */
data class Player(val email: String, val name: String, val corporationName: String, val money: Int, val ships: List<Ship>,) {

	companion object {
		/** A player in the lobby: money and ships arrive when the game starts (R40). */
		fun newcomer(email: String, name: String, corporationName: String) = Player(email, name, corporationName, 0, emptyList())
	}
}
