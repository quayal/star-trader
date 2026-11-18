package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.InterceptionRoll
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** An [InterceptionRoll] as the attribute map nested in its notice (engineering standards §12). */
internal class InterceptionRollItem(val attributes: Map<String, AttributeValue>) {

	constructor(roll: InterceptionRoll) : this(
		mapOf(
			INTERCEPTOR_EMAIL to AttributeValue.fromS(roll.interceptorEmail),
			INTERCEPTOR_SHIP to AttributeValue.fromS(roll.interceptorShip),
			INTERCEPTOR_ROLL to AttributeValue.fromS(roll.interceptorRoll),
			ARRIVING_EMAIL to AttributeValue.fromS(roll.arrivingEmail),
			ARRIVING_SHIP to AttributeValue.fromS(roll.arrivingShip),
			ARRIVING_ROLL to AttributeValue.fromS(roll.arrivingRoll),
			SUCCESS to AttributeValue.fromBool(roll.success)
		)
	)

	fun toInterceptionRoll(): InterceptionRoll = InterceptionRoll(
		interceptorEmail = attributes.attribute(INTERCEPTOR_EMAIL).s(),
		interceptorShip = attributes.attribute(INTERCEPTOR_SHIP).s(),
		interceptorRoll = attributes.attribute(INTERCEPTOR_ROLL).s(),
		arrivingEmail = attributes.attribute(ARRIVING_EMAIL).s(),
		arrivingShip = attributes.attribute(ARRIVING_SHIP).s(),
		arrivingRoll = attributes.attribute(ARRIVING_ROLL).s(),
		success = attributes.attribute(SUCCESS).bool()
	)

	private companion object {
		const val INTERCEPTOR_EMAIL = "interceptorEmail"
		const val INTERCEPTOR_SHIP = "interceptorShip"
		const val INTERCEPTOR_ROLL = "interceptorRoll"
		const val ARRIVING_EMAIL = "arrivingEmail"
		const val ARRIVING_SHIP = "arrivingShip"
		const val ARRIVING_ROLL = "arrivingRoll"
		const val SUCCESS = "success"
	}
}
