package dev.adriangrzebyk.trader.game

/** The game's dice: random in play, fixed rolls in tests (engineering standards §4). */
interface Dice {

	/** A d2: 1 or 2. Initiative is bid + d2, with ties rolled again (R27). */
	fun d2(): Int

	/** Two d6 summed: 2 to 12. A Trading resolution's counter starts at the good's S/D plus this roll (R34). */
	fun twoD6(): Int

	/** A d6: 1 to 6. Interception adds one to each side (R70); a salvo rolls one and a rocket salvo that hits one more to destroy (R75). */
	fun d6(): Int

	/** A d3: 1 to 3. Each player starts with 3 + d3 connection points to place (R82). */
	fun d3(): Int
}
