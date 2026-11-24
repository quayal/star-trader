package dev.adriangrzebyk.trader.game

class ControlService(private val gameStore: GameStore, private val dice: Dice) {

	/**
	 * [callerEmail] answers whether to back the corporation under reputation control (R129) inside [GameStore.update], so two
	 * players answering at once leave one backer and the second roll is made once (A04).
	 */
	fun answerBacking(gameId: String, callerEmail: String, back: Boolean): Game =
		gameStore.update(gameId) { it.answerBacking(callerEmail, back, dice) }
}
