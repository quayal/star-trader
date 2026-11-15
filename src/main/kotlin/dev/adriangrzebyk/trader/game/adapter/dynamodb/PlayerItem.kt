package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Player
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Player] as the attribute map nested in the game item; each ship is a [ShipItem], each market position a [MarketPositionItem], each warehouse a [WarehouseItem], each factory a [FactoryItem]. */
internal class PlayerItem(val attributes: Map<String, AttributeValue>) {

	constructor(player: Player) : this(
		mapOf(
			EMAIL to AttributeValue.fromS(player.email),
			NAME to AttributeValue.fromS(player.name),
			CORPORATION_NAME to AttributeValue.fromS(player.corporationName),
			MONEY to number(player.money),
			SHIPS to AttributeValue.fromL(player.ships.map { AttributeValue.fromM(ShipItem(it).attributes) }),
			MARKET_POSITIONS to AttributeValue.fromL(player.marketPositions.map { AttributeValue.fromM(MarketPositionItem(it).attributes) }),
			WAREHOUSES to AttributeValue.fromL(player.warehouses.map { AttributeValue.fromM(WarehouseItem(it).attributes) }),
			FACTORIES to AttributeValue.fromL(player.factories.map { AttributeValue.fromM(FactoryItem(it).attributes) })
		)
	)

	fun toPlayer(): Player = Player(
		email = attributes.attribute(EMAIL).s(),
		name = attributes.attribute(NAME).s(),
		corporationName = attributes.attribute(CORPORATION_NAME).s(),
		money = attributes.attribute(MONEY).n().toInt(),
		ships = attributes.attribute(SHIPS).l().map { ShipItem(it.m()).toShip() },
		// Items saved before E03-S02 have no positions; nobody held one then (R40, BACKWARD_COMPATIBILITY.md §7).
		marketPositions = attributes[MARKET_POSITIONS]?.l()?.map { MarketPositionItem(it.m()).toMarketPosition() } ?: emptyList(),
		// Items saved before E06-S02 have no warehouses or factories; nobody had one then (R40, BACKWARD_COMPATIBILITY.md §7).
		warehouses = attributes[WAREHOUSES]?.l()?.map { WarehouseItem(it.m()).toWarehouse() } ?: emptyList(),
		factories = attributes[FACTORIES]?.l()?.map { FactoryItem(it.m()).toFactory() } ?: emptyList()
	)

	private companion object {
		const val EMAIL = "email"
		const val NAME = "name"
		const val CORPORATION_NAME = "corporationName"
		const val MONEY = "money"
		const val SHIPS = "ships"
		const val MARKET_POSITIONS = "marketPositions"
		const val WAREHOUSES = "warehouses"
		const val FACTORIES = "factories"
	}
}
