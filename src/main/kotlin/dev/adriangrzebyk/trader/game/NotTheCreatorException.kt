package dev.adriangrzebyk.trader.game

class NotTheCreatorException(message: String = "Only the creator can start the game") : RuntimeException(message)
