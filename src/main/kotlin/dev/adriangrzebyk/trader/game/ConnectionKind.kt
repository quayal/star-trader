package dev.adriangrzebyk.trader.game

/** R81: the three kinds of connections a player has, with the name the log and screens use. */
enum class ConnectionKind(val displayName: String) {
	POLITICAL("political"),
	BUSINESS("business"),
	CRIMINAL("criminal")
}
