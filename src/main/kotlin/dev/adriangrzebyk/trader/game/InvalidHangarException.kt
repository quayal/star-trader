package dev.adriangrzebyk.trader.game

/** A Parking Hangar request that breaks a rule (R127): no hangar there, or not enough money for the fee; [message] says what to fix. */
class InvalidHangarException(message: String) : RuntimeException(message)
