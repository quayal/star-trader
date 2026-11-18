package dev.adriangrzebyk.trader.game

/** A finished game's result (E05-S08, R39), public: every player, most money first, and the [winners], several on a tie. */
data class GameResult(val standings: List<Player>, val winners: List<Player>)
