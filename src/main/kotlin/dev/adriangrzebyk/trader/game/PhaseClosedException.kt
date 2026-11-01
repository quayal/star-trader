package dev.adriangrzebyk.trader.game

/** An action for [phase] arrived after that phase closed, so it wasn't applied (A04). */
class PhaseClosedException(phase: TurnPhase) : RuntimeException("${phase.displayName} has closed")
