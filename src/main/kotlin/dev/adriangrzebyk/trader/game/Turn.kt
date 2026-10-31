package dev.adriangrzebyk.trader.game

/** The turn a running game is in (R41: a game starts in turn 1, Bidding). */
data class Turn(val number: Int, val phase: TurnPhase,)
