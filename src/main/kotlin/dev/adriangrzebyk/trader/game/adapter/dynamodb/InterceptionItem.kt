package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Interception
import dev.adriangrzebyk.trader.game.InterceptionAnswer
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** An [Interception] as the attribute map nested in its turn's item (engineering standards §12). */
internal class InterceptionItem(val attributes: Map<String, AttributeValue>) {

	constructor(interception: Interception) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(interception.system.name),
			SHIP to AttributeValue.fromS(interception.ship),
			OWNER_EMAIL to AttributeValue.fromS(interception.ownerEmail),
			ASKED to strings(interception.asked),
			ANSWERS to AttributeValue.fromL(
				interception.answers.map {
					AttributeValue.fromM(
						mapOf(PLAYER_EMAIL to AttributeValue.fromS(it.playerEmail)) +
							listOfNotNull(it.ship?.let { ship -> SHIP_TRYING to AttributeValue.fromS(ship) })
					)
				}
			)
		)
	)

	fun toInterception(): Interception = Interception(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		ship = attributes.attribute(SHIP).s(),
		ownerEmail = attributes.attribute(OWNER_EMAIL).s(),
		asked = attributes.attribute(ASKED).l().map { it.s() },
		answers = attributes.attribute(ANSWERS).l().map { answer ->
			val fields = answer.m()
			InterceptionAnswer(playerEmail = fields.attribute(PLAYER_EMAIL).s(), ship = fields[SHIP_TRYING]?.s())
		}
	)

	private companion object {
		const val SYSTEM = "system"
		const val SHIP = "ship"
		const val OWNER_EMAIL = "ownerEmail"
		const val ASKED = "asked"
		const val ANSWERS = "answers"
		const val PLAYER_EMAIL = "playerEmail"
		const val SHIP_TRYING = "shipTrying"
	}
}
