package dev.adriangrzebyk.trader.game

/** A ship jump that breaks a jump rule (R26, S02-3, S02-4), so it wasn't made; [message] says what to fix. */
class InvalidJumpException(message: String) : RuntimeException(message)
