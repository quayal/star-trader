package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [RecruitedAgent] as the attribute map nested in its player's item: aboard a ship by name, or on its own at a system and location. */
internal class RecruitedAgentItem(val attributes: Map<String, AttributeValue>) {

	constructor(recruited: RecruitedAgent) : this(
		mapOf(AGENT to AttributeValue.fromS(recruited.agent.name)) +
			listOfNotNull(recruited.placeableIn?.let { PLACEABLE_IN to AttributeValue.fromN(it.toString()) }) +
			when (val position = recruited.position) {
				is AgentPosition.Aboard -> mapOf(SHIP to AttributeValue.fromS(position.shipName))
				is AgentPosition.OnOwn -> mapOf(
					SYSTEM to AttributeValue.fromS(position.system.name),
					LOCATION to AttributeValue.fromS(position.location.name)
				)
			}
	)

	fun toRecruitedAgent(): RecruitedAgent = RecruitedAgent(
		agent = Agent.valueOf(attributes.attribute(AGENT).s()),
		position = attributes[SHIP]?.let { AgentPosition.Aboard(it.s()) }
			?: AgentPosition.OnOwn(StarSystem.valueOf(attributes.attribute(SYSTEM).s()), ShipLocation.valueOf(attributes.attribute(LOCATION).s())),
		// Absent from items saved before 2026-11-21, and once placed.
		placeableIn = attributes[PLACEABLE_IN]?.n()?.toInt()
	)

	private companion object {
		const val AGENT = "agent"
		const val SHIP = "ship"
		const val SYSTEM = "system"
		const val LOCATION = "location"
		const val PLACEABLE_IN = "placeableIn"
	}
}
