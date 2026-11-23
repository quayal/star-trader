package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem

/**
 * A route passengers travel from [from]'s Port to [to]'s (R125). Each Opportunity offers [groups] groups on it: the two
 * Ports' classes added.
 */
data class PassengerRoute(val from: StarSystem, val to: StarSystem) {

	init {
		require(from != to) { "Passengers travel between two systems." }
	}

	/** The groups on offer on this route each Opportunity (R125). */
	fun groups(): Int = from.locations.portClass + to.locations.portClass

	companion object {
		/** Every route between two different systems, in map order. */
		fun all(): List<PassengerRoute> = StarSystem.entries.flatMap { from ->
			StarSystem.entries.filter { it != from }.map { PassengerRoute(from, it) }
		}
	}
}
