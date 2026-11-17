package dev.adriangrzebyk.trader.game

/** A battle order, hit placement or landing that breaks a battle rule (R74–R77), so it wasn't applied; [message] says what to fix. */
class InvalidBattleException(message: String) : RuntimeException(message)
