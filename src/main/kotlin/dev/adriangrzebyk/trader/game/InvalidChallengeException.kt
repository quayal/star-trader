package dev.adriangrzebyk.trader.game

/** A challenge of a market position that the rules refuse (R126): a message the player can act on. */
class InvalidChallengeException(message: String) : RuntimeException(message)
