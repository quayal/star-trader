package dev.adriangrzebyk.trader.game

/** A trader's amount or ship split that breaks a rule of the game (R34, R12, R10, R42), so nothing traded. */
class InvalidTradeException(message: String) : RuntimeException(message)
