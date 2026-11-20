package dev.adriangrzebyk.trader.game.agent

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.ShipLocation

/** Where an agent is (R104): aboard one of its owner's ships, or on its own in a Port or on a Planet, never alone in Space. */
sealed interface AgentPosition {

	/** Aboard the ship [shipName]: it travels with the ship (R104) and takes no cargo space. */
	data class Aboard(val shipName: String) : AgentPosition

	/** On its own at [location] (Port or Planet) of [system]. */
	data class OnOwn(val system: StarSystem, val location: ShipLocation) : AgentPosition {
		init {
			require(location == ShipLocation.PORT || location == ShipLocation.PLANET) { "An agent is on its own only in a Port or on a Planet." }
		}
	}
}
