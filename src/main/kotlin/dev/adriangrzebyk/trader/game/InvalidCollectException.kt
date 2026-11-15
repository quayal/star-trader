package dev.adriangrzebyk.trader.game

/** A collect of factory output that can't be applied (R47): refused with a message the player can act on. */
class InvalidCollectException(message: String) : RuntimeException(message)
