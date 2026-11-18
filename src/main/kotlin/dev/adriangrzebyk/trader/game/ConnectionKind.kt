package dev.adriangrzebyk.trader.game

/**
 * R81: the three kinds of connections a player has, with the name the log and screens use, and what raising one point
 * does to its player's reputation (R86, E13-S02): business +2, political +1, criminal −1.
 */
enum class ConnectionKind(val displayName: String, val reputationChange: Int) {
	POLITICAL("political", 1),
	BUSINESS("business", 2),
	CRIMINAL("criminal", -1)
}
