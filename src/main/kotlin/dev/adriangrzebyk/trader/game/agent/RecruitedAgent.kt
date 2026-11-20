package dev.adriangrzebyk.trader.game.agent

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.ShipLocation

/** One of a player's agents (R103): which one, and where it is. Its identity is its owner's alone (R105). */
data class RecruitedAgent(val agent: Agent, val position: AgentPosition) {

	/** Whether it's aboard the ship [shipName]. */
	fun isAboard(shipName: String): Boolean = (position as? AgentPosition.Aboard)?.shipName == shipName

	/** Whether it's on its own at [location] of [system]. */
	fun isOnOwnAt(system: StarSystem, location: ShipLocation): Boolean = position == AgentPosition.OnOwn(system, location)

	/** This agent at [to]. */
	fun movedTo(to: AgentPosition): RecruitedAgent = copy(position = to)
}
