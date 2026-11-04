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
			OFFERS to AttributeValue.fromL(turn.offers.map { AttributeValue.fromM(OfferItem(it).attributes) }),
			BIDS to AttributeValue.fromL(turn.bids.map { AttributeValue.fromM(InitiativeBidItem(it).attributes) }),
			INITIATIVE to AttributeValue.fromL(turn.initiative.map { AttributeValue.fromS(it) }),
			TURN_ORDER_PICKS to AttributeValue.fromL(turn.turnOrderPicks.map { AttributeValue.fromM(TurnOrderPickItem(it).attributes) }),
			JUMPED_SHIPS to AttributeValue.fromL(turn.jumpedShips.map { AttributeValue.fromS(it) }),
			DONE_JUMPING to AttributeValue.fromL(turn.doneJumping.map { AttributeValue.fromS(it) })
		)
	)

	fun toTurn(): Turn = Turn(
		number = attributes.attribute(NUMBER).n().toInt(),
		phase = TurnPhase.valueOf(attributes.attribute(PHASE).s()),
		// Items saved before E03-S01 have no done list; nobody had signalled done then (BACKWARD_COMPATIBILITY.md §7).
		doneBidding = attributes[DONE_BIDDING]?.l()?.map { it.s() } ?: emptyList(),
		// Items saved before E03-S03 have no offers; nobody could place one then (BACKWARD_COMPATIBILITY.md §7).
		offers = attributes[OFFERS]?.l()?.map { OfferItem(it.m()).toOffer() } ?: emptyList(),
		// Items saved before E03-S05 have no bids or initiative order; nobody could bid then (BACKWARD_COMPATIBILITY.md §7).
		bids = attributes[BIDS]?.l()?.map { InitiativeBidItem(it.m()).toBid() } ?: emptyList(),
		initiative = attributes[INITIATIVE]?.l()?.map { it.s() } ?: emptyList(),
		// Items saved before E04-S01 have no picks; nobody could pick a place then (BACKWARD_COMPATIBILITY.md §7).
		turnOrderPicks = attributes[TURN_ORDER_PICKS]?.l()?.map { TurnOrderPickItem(it.m()).toPick() } ?: emptyList(),
		// Items saved before E04-S02 have no jumps; nobody could jump a ship then (BACKWARD_COMPATIBILITY.md §7).
		jumpedShips = attributes[JUMPED_SHIPS]?.l()?.map { it.s() } ?: emptyList(),
		doneJumping = attributes[DONE_JUMPING]?.l()?.map { it.s() } ?: emptyList()
	)

	private companion object {
		const val NUMBER = "number"
		const val PHASE = "phase"
		const val DONE_BIDDING = "doneBidding"
		const val OFFERS = "offers"
		const val BIDS = "bids"
		const val INITIATIVE = "initiative"
		const val TURN_ORDER_PICKS = "turnOrderPicks"
		const val JUMPED_SHIPS = "jumpedShips"
		const val DONE_JUMPING = "doneJumping"
	}
}
