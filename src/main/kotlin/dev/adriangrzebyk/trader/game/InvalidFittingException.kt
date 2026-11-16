package dev.adriangrzebyk.trader.game

/** A ship fitted with modules its hull can't take (R52), so it wasn't built; [message] says what to fix. */
class InvalidFittingException(message: String) : RuntimeException(message)
