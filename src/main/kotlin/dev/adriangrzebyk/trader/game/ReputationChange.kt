package dev.adriangrzebyk.trader.game

/** A player after a reputation change, [player], with the public [lines] that tell it (R86, R105). */
data class ReputationChange(val player: Player, val lines: List<String>)
