package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Ship] as the attribute map nested in its player's item (engineering standards §12). */
internal object ShipAttributes {

	private const val NAME = "name"
	private const val SYSTEM = "system"
	private const val LOCATION = "location"

	fun toAttributes(ship: Ship): Map<String, AttributeValue> = mapOf(
		NAME to AttributeValue.fromS(ship.name),
		SYSTEM to AttributeValue.fromS(ship.system.name),
		LOCATION to AttributeValue.fromS(ship.location.name),
	)

	fun from(attributes: Map<String, AttributeValue>): Ship = Ship(
		name = attributes.attribute(NAME).s(),
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		location = ShipLocation.valueOf(attributes.attribute(LOCATION).s()),
	)
}
