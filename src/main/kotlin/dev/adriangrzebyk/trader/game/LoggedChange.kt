package dev.adriangrzebyk.trader.game

/** A player after a change, [player], with the public [lines] that tell it: a reputation change (R86, R105), a looting (E12). */
data class LoggedChange(val player: Player, val lines: List<String>)
