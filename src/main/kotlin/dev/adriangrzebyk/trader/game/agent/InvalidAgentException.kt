package dev.adriangrzebyk.trader.game.agent

/** An agent action the rules refuse as sent (R103–R106): the player can correct it. */
class InvalidAgentException(message: String) : RuntimeException(message)
