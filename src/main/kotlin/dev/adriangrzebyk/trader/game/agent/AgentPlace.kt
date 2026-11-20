package dev.adriangrzebyk.trader.game.agent

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.ShipLocation

/** Where an agent stands on the board (R104): a [system] and the [location] in it, its ship's when it's aboard one. */
data class AgentPlace(val system: StarSystem, val location: ShipLocation) {

	/** Whether this is a ship's place in Space, where an agent never stands on its own and doesn't move (R104). */
	fun isInSpace(): Boolean = location == ShipLocation.SPACE
}

/** How many of one player's agents are on their own at [place] (R105): public, unlike who they are. */
data class AgentCount(val place: AgentPlace, val count: Int)
