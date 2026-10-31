package dev.adriangrzebyk.trader.game

/** A player's membership in one game; [email] is the membership key. Resources arrive when the game starts (E02-S05). */
data class Player(val email: String, val name: String, val corporationName: String,)
