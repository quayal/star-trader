package dev.adriangrzebyk.trader.game

/** Says nothing about the game itself: a non-member learns only that they can't see it (A05). */
class NotAGameMemberException : RuntimeException("You are not a player in this game")
