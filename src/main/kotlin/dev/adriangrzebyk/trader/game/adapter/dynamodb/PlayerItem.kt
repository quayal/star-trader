package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Player
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Player] as the attribute map nested in the game item; each ship is a [ShipItem], each market position a [MarketPositionItem]. */
internal class PlayerItem(val attributes: Map<String, AttributeValue>) {

	constructor(player: Player) : this(
		mapOf(
			EMAIL to AttributeValue.fromS(player.email),
			NAME to AttributeValue.fromS(player.name),
			CORPORATION_NAME to AttributeValue.fromS(player.corporationName),
			MONEY to number(player.money),
			SHIPS to AttributeValue.fromL(player.ships.map { AttributeValue.fromM(ShipItem(it).attributes) }),
			MARKET_POSITIONS to AttributeValue.fromL(player.marketPositions.map { AttributeValue.fromM(MarketPositionItem(it).attributes) }),
			PENDING_POSITIONS to AttributeValue.fromL(player.pendingPositions.map { AttributeValue.fromM(MarketPositionItem(it).attributes) })
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
		// Items saved before E05-S06 have none: no position could change then.
		pendingPositions = attributes[PENDING_POSITIONS]?.l()?.map { MarketPositionItem(it.m()).toMarketPosition() } ?: emptyList()
	)

	private companion object {
		const val EMAIL = "email"
		const val NAME = "name"
		const val CORPORATION_NAME = "corporationName"
		const val MONEY = "money"
		const val SHIPS = "ships"
		const val MARKET_POSITIONS = "marketPositions"
		const val PENDING_POSITIONS = "pendingPositions"
	}
}
