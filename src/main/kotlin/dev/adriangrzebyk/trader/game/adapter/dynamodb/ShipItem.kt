package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.CrewClass
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TipOff
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
			CREW_CLASS to AttributeValue.fromS(ship.crewClass.name),
			HULL_DAMAGE to number(ship.hullDamage),
			DAMAGED_MODULES to AttributeValue.fromL(ship.damagedModules.map { AttributeValue.fromS(it.name) }),
			SPECIAL_LOTS to AttributeValue.fromL(
				ship.specialLots.map {
					AttributeValue.fromM(
						mapOf(
							GOODS to AttributeValue.fromS(it.goods.name),
							UNITS to number(it.units),
							SELL_AT to AttributeValue.fromS(it.sellAt.name)
						)
					)
				}
			)
		) + listOfNotNull(
			ship.boughtOnTurn?.let { BOUGHT_ON_TURN to number(it) },
			ship.deliveryJob?.let { DELIVERY_JOB to AttributeValue.fromS(it.name) }
		)
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
		crewClass = attributes[CREW_CLASS]?.s()?.let { CrewClass.valueOf(it) } ?: Ship.STARTING_CREW_CLASS,
		boughtOnTurn = attributes[BOUGHT_ON_TURN]?.n()?.toInt(),
		// Items saved before E10 have no damage: nothing could damage a ship then.
		hullDamage = attributes[HULL_DAMAGE]?.n()?.toInt() ?: 0,
		damagedModules = attributes[DAMAGED_MODULES]?.l()?.map { Module.valueOf(it.s()) } ?: emptyList(),
		// Items saved before tip-offs (R112, R114) carry neither.
		specialLots = attributes[SPECIAL_LOTS]?.l().orEmpty().map { lot ->
			SpecialLot(
				SpecialGoods.valueOf(lot.m().attribute(GOODS).s()),
				lot.m().attribute(UNITS).n().toInt(),
				StarSystem.valueOf(lot.m().attribute(SELL_AT).s())
			)
		},
		deliveryJob = attributes[DELIVERY_JOB]?.s()?.let { TipOff.valueOf(it) }
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
		const val HULL_DAMAGE = "hullDamage"
		const val DAMAGED_MODULES = "damagedModules"
		const val SPECIAL_LOTS = "specialLots"
		const val GOODS = "goods"
		const val UNITS = "units"
		const val SELL_AT = "sellAt"
		const val DELIVERY_JOB = "deliveryJob"
	}
}
