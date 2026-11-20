package dev.adriangrzebyk.trader.game

/**
 * The phases of a turn, in the order every turn runs them: this order is the one place the phase list lives (R44,
 * E06-S01-5), so a phase added between two of these is a new entry here and its own rules, nothing else.
 */
enum class TurnPhase(val displayName: String) {
	BIDDING("Bidding"),
	SHIP_JUMPS("Ship-jumps"),
	TRADING("Trading"),
	OPPORTUNITY("Opportunity"),
	INVESTMENT("Investment");

	/** The phase that follows this one in a turn, or null for the last (R44). */
	fun next(): TurnPhase? = entries.getOrNull(ordinal + 1)
}
