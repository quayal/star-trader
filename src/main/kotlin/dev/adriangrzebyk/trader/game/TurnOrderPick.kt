package dev.adriangrzebyk.trader.game

/** [playerEmail] holds [place] (1 = first) in this turn's turn order (R26). Public: everyone sees the order. */
data class TurnOrderPick(val playerEmail: String, val place: Int)
