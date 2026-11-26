package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.control.PoliceOdds
import dev.adriangrzebyk.trader.game.smuggling.LandingOdds

/**
 * What waits for one of a player's ships at [system] if it jumps there this turn (R139): its [landing] odds on the Planet
 * (R116–R118), its [police] odds if it stays in Space through Control (R128), and whether the Port turns it away,
 * [portRefused] (R131). Private to the ship's owner, like the ship's stats (R57).
 */
data class ShipDestination(val system: StarSystem, val landing: LandingOdds, val police: PoliceOdds, val portRefused: Boolean)
