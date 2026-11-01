package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Player
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Player] as the attribute map nested in the game item; each ship is a [ShipItem]. */
internal class PlayerItem(val attributes: Map<String, AttributeValue>) {

	constructor(player: Player) : this(
		mapOf(
			EMAIL to AttributeValue.fromS(player.email),
			NAME to AttributeValue.fromS(player.name),
			CORPORATION_NAME to AttributeValue.fromS(player.corporationName),
			MONEY to number(player.money),
			SHIPS to AttributeValue.fromL(player.ships.map { AttributeValue.fromM(ShipItem(it).attributes) })
		)
	)

	fun toPlayer(): Player = Player(
		email = attributes.attribute(EMAIL).s(),
		name = attributes.attribute(NAME).s(),
		corporationName = attributes.attribute(CORPORATION_NAME).s(),
		money = attributes.attribute(MONEY).n().toInt(),
		ships = attributes.attribute(SHIPS).l().map { ShipItem(it.m()).toShip() }
	)

	private companion object {
		const val EMAIL = "email"
		const val NAME = "name"
		const val CORPORATION_NAME = "corporationName"
		const val MONEY = "money"
		const val SHIPS = "ships"
	}
}
