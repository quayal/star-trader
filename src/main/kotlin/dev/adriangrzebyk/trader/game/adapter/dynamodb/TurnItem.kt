package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Turn] as the attribute map nested in the game item (engineering standards §12). */
internal class TurnItem(val attributes: Map<String, AttributeValue>) {

	constructor(turn: Turn) : this(
		mapOf(
			NUMBER to number(turn.number),
			PHASE to AttributeValue.fromS(turn.phase.name),
			DONE_BIDDING to AttributeValue.fromL(turn.doneBidding.map { AttributeValue.fromS(it) }),
			OFFERS to AttributeValue.fromL(turn.offers.map { AttributeValue.fromM(OfferItem(it).attributes) })
		)
	)

	fun toTurn(): Turn = Turn(
		number = attributes.attribute(NUMBER).n().toInt(),
		phase = TurnPhase.valueOf(attributes.attribute(PHASE).s()),
		// Items saved before E03-S01 have no done list; nobody had signalled done then (BACKWARD_COMPATIBILITY.md §7).
		doneBidding = attributes[DONE_BIDDING]?.l()?.map { it.s() } ?: emptyList(),
		// Items saved before E03-S03 have no offers; nobody could place one then (BACKWARD_COMPATIBILITY.md §7).
		offers = attributes[OFFERS]?.l()?.map { OfferItem(it.m()).toOffer() } ?: emptyList()
	)

	private companion object {
		const val NUMBER = "number"
		const val PHASE = "phase"
		const val DONE_BIDDING = "doneBidding"
		const val OFFERS = "offers"
	}
}
