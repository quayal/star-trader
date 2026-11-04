package dev.adriangrzebyk.trader.game

/** An ordered step taken by a player whose step it isn't (R04), so it wasn't applied. */
class NotYourTurnException(message: String) : RuntimeException(message)
