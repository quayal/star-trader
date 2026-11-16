package dev.adriangrzebyk.trader.game

/** A purchase in Investment that can't be made (R45, R46): refused with a message the player can act on. */
class InvalidInvestmentException(message: String) : RuntimeException(message)
