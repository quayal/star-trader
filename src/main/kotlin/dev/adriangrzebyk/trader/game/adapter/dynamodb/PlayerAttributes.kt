package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Player
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Player] as the attribute map nested in the game item; its ships go through [ShipAttributes]. */
internal object PlayerAttributes {

	private const val EMAIL = "email"
	private const val NAME = "name"
	private const val CORPORATION_NAME = "corporationName"
	private const val MONEY = "money"
	private const val SHIPS = "ships"

	fun toAttributes(player: Player): Map<String, AttributeValue> = mapOf(
		EMAIL to AttributeValue.fromS(player.email),
		NAME to AttributeValue.fromS(player.name),
		CORPORATION_NAME to AttributeValue.fromS(player.corporationName),
		MONEY to number(player.money),
		SHIPS to AttributeValue.fromL(player.ships.map { AttributeValue.fromM(ShipAttributes.toAttributes(it)) }),
	)

	fun from(attributes: Map<String, AttributeValue>): Player = Player(
		email = attributes.attribute(EMAIL).s(),
		name = attributes.attribute(NAME).s(),
		corporationName = attributes.attribute(CORPORATION_NAME).s(),
		money = attributes.attribute(MONEY).n().toInt(),
		ships = attributes.attribute(SHIPS).l().map { ShipAttributes.from(it.m()) },
	)
}
