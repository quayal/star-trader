package dev.adriangrzebyk.trader.game

/** A move of goods between a ship in port and a warehouse that can't be applied (R58): refused with a message the player can act on. */
class InvalidCargoMoveException(message: String) : RuntimeException(message)
