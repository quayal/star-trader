package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.CrewClass
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.Module
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
			CARGO to AttributeValue.fromM(ship.cargo.entries.associate { (goods, units) -> goods.name to number(units) }),
			HULL to AttributeValue.fromS(ship.hull.name),
			MODULES to AttributeValue.fromL(ship.modules.map { AttributeValue.fromS(it.name) }),
			CREW_CLASS to AttributeValue.fromS(ship.crewClass.name)
		) + listOfNotNull(ship.boughtOnTurn?.let { BOUGHT_ON_TURN to number(it) })
	)

	fun toShip(): Ship = Ship(
		name = attributes.attribute(NAME).s(),
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		location = ShipLocation.valueOf(attributes.attribute(LOCATION).s()),
		// Items saved before E03-S02 have no cargo; every ship was empty then (BACKWARD_COMPATIBILITY.md §7).
		cargo = attributes[CARGO]?.m()?.entries?.associate { (goods, units) -> Goods.valueOf(goods) to units.n().toInt() } ?: emptyMap(),
		// Items saved before E07-S01 have no hull or modules; every ship was a Clarinet with 5 Cargo modules then.
		hull = attributes[HULL]?.s()?.let { Hull.valueOf(it) } ?: Ship.LEGACY_HULL,
		modules = attributes[MODULES]?.l()?.map { Module.valueOf(it.s()) } ?: Ship.LEGACY_MODULES,
		// Items saved before E09 have no crew class (every ship was a starting ship, class B, R55) and were never bought.
		crewClass = attributes[CREW_CLASS]?.s()?.let { CrewClass.valueOf(it) } ?: CrewClass.B,
		boughtOnTurn = attributes[BOUGHT_ON_TURN]?.n()?.toInt()
	)

	private companion object {
		const val NAME = "name"
		const val SYSTEM = "system"
		const val LOCATION = "location"
		const val CARGO = "cargo"
		const val HULL = "hull"
		const val MODULES = "modules"
		const val CREW_CLASS = "crewClass"
		const val BOUGHT_ON_TURN = "boughtOnTurn"
	}
}
