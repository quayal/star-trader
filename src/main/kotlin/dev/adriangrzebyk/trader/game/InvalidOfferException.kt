package dev.adriangrzebyk.trader.game

/** An offer that breaks a rule of the game (R03, R33, R38), so it wasn't placed. */
class InvalidOfferException(message: String) : RuntimeException(message)
