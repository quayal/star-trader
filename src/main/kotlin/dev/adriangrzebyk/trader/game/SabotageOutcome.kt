package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods

/**
 * What a sabotage's effect did to its target's [owner] (R99), from [Player.sufferSabotage]. [lost] is what the target lost
 * once the effect is settled, or null while it waits for a choice: the saboteur's of a warehouse's missing units, or the
 * owner's of a ship's hits. [logged] is the number the public line gives (hits, units missing, turns disabled), or null
 * when nothing happened and nothing is logged (R101). [destroyed] says the hits destroyed the ship at once.
 */
data class SabotageOutcome(val owner: Player, val lost: Map<Goods, Int>?, val logged: Int?, val destroyed: Boolean)
