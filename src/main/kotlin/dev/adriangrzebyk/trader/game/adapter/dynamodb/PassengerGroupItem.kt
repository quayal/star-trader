package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.PassengerGroup
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [PassengerGroup] as the attribute map nested in its player's item (engineering standards §12). */
internal class PassengerGroupItem(val attributes: Map<String, AttributeValue>) {

	constructor(group: PassengerGroup) : this(
		mapOf(
			SHIP to AttributeValue.fromS(group.ship),
			FROM to AttributeValue.fromS(group.from.name),
			TO to AttributeValue.fromS(group.to.name),
			GROUPS to number(group.groups)
		)
	)

	fun toPassengerGroup(): PassengerGroup = PassengerGroup(
		ship = attributes.attribute(SHIP).s(),
		from = StarSystem.valueOf(attributes.attribute(FROM).s()),
		to = StarSystem.valueOf(attributes.attribute(TO).s()),
		groups = attributes.attribute(GROUPS).n().toInt()
	)

	private companion object {
		const val SHIP = "ship"
		const val FROM = "from"
		const val TO = "to"
		const val GROUPS = "groups"
	}
}
