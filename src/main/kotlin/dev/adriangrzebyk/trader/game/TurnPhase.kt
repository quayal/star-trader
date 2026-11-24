package dev.adriangrzebyk.trader.game

/**
 * The phases of a turn, in the order every turn runs them: this order is the one place the phase list lives (R44,
 * E06-S01-5), so a phase added between two of these is a new entry here and its own rules, nothing else. Since
 * 2026-11-20 the creator's full order: Bidding, Initiative (picking the turn order, split out of Ship-jumps), News (R109),
 * Jumps (renamed from Ship-jumps), Trading, Opportunity, Investment, Control (R128–R131: police in Space, reputation control,
 * recovery, bonus), which closes the turn.
 */
enum class TurnPhase(val displayName: String) {
	BIDDING("Bidding"),
	INITIATIVE("Initiative"),
	NEWS("News"),
	JUMPS("Jumps"),
	TRADING("Trading"),
	OPPORTUNITY("Opportunity"),
	INVESTMENT("Investment"),
	CONTROL("Control");

	/** The phase that follows this one in a turn, or null for the last (R44). */
	fun next(): TurnPhase? = entries.getOrNull(ordinal + 1)
}
