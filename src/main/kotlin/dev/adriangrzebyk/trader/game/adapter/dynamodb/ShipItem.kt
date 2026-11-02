package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Ship] as the attribute map nested in its player's item (engineering standards §12). */
internal class ShipItem(val attributes: Map<String, AttributeValue>) {

	constructor(ship: Ship) : this(
		mapOf(
			NAME to AttributeValue.fromS(ship.name),
			SYSTEM to AttributeValue.fromS(ship.system.name),
			LOCATION to AttributeValue.fromS(ship.location.name),
			CARGO to AttributeValue.fromM(ship.cargo.entries.associate { (goods, units) -> goods.name to number(units) })
		)
	)

	fun toShip(): Ship = Ship(
		name = attributes.attribute(NAME).s(),
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		location = ShipLocation.valueOf(attributes.attribute(LOCATION).s()),
		// Items saved before E03-S02 have no cargo; every ship was empty then (BACKWARD_COMPATIBILITY.md §7).
		cargo = attributes[CARGO]?.m()?.entries?.associate { (goods, units) -> Goods.valueOf(goods) to units.n().toInt() } ?: emptyMap()
	)

	private companion object {
		const val NAME = "name"
		const val SYSTEM = "system"
		const val LOCATION = "location"
		const val CARGO = "cargo"
	}
}
