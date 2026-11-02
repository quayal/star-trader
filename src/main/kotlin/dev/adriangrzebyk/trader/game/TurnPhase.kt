package dev.adriangrzebyk.trader.game

/** The phases of a turn, in the order every turn runs them (N06, R04). */
enum class TurnPhase(val displayName: String) {
	BIDDING("Bidding"),
	SHIP_JUMPS("Ship-jumps"),
	TRADING("Trading")
}
