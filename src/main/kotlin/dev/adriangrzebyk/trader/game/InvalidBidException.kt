package dev.adriangrzebyk.trader.game

/** An initiative bid that breaks a rule of the game (R27), so it wasn't placed. */
class InvalidBidException(message: String) : RuntimeException(message)
