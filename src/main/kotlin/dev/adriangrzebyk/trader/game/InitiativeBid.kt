package dev.adriangrzebyk.trader.game

/**
 * [playerEmail]'s money bid for this turn's initiative (R27). Secret: only its player sees it (R29), and it's paid
 * when Bidding closes, win or lose.
 */
data class InitiativeBid(val playerEmail: String, val amount: Int)
