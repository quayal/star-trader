package dev.adriangrzebyk.trader.game.agent

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.ShipLocation

/**
 * One of a player's agents (R103): which one, and where it is. Its identity is its owner's alone (R105). [placeableIn] is
 * the turn it was recruited in, while its player may still choose where it starts (playtest, 2026-11-21: placed after
 * recruiting, once they know who joined); null once placed, and for an agent bought from another player.
 */
data class RecruitedAgent(val agent: Agent, val position: AgentPosition, val placeableIn: Int? = null) {

	/** Whether its player may still choose where it starts, in turn [turn]. */
	fun isPlaceableIn(turn: Int): Boolean = placeableIn == turn

	/** This agent started at [to]: placed once, it moves like any agent from then on. */
	fun placedAt(to: AgentPosition): RecruitedAgent = copy(position = to, placeableIn = null)

	/** Whether it's aboard the ship [shipName]. */
	fun isAboard(shipName: String): Boolean = (position as? AgentPosition.Aboard)?.shipName == shipName

	/** Whether it's on its own at [location] of [system]. */
	fun isOnOwnAt(system: StarSystem, location: ShipLocation): Boolean = position == AgentPosition.OnOwn(system, location)

	/** This agent at [to]. */
	fun movedTo(to: AgentPosition): RecruitedAgent = copy(position = to)
}
