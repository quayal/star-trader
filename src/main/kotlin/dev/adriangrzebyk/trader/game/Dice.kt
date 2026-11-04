package dev.adriangrzebyk.trader.game

/** The game's dice: random in play, fixed rolls in tests (engineering standards §4). */
interface Dice {

	/** A d2: 1 or 2. Initiative is bid + d2, with ties rolled again (R27). */
	fun d2(): Int
}
