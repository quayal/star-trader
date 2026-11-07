package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.MarketGood
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
			DONE_JUMPING to AttributeValue.fromL(turn.doneJumping.map { AttributeValue.fromS(it) }),
			TRADING_QUEUE to AttributeValue.fromL(turn.tradingQueue.map { AttributeValue.fromM(MarketGoodItem(it).attributes) }),
			RESOLVED_GOODS to AttributeValue.fromL(turn.resolvedGoods.map { AttributeValue.fromM(MarketGoodItem(it).attributes) })
		) + listOfNotNull(
			turn.landingShip?.let { LANDING_SHIP to AttributeValue.fromS(it) },
			turn.resolution?.let { RESOLUTION to AttributeValue.fromM(ResolutionItem(it).attributes) }
		)
	)

	fun toTurn(): Turn {
		val phase = TurnPhase.valueOf(attributes.attribute(PHASE).s())
		return Turn(
			number = attributes.attribute(NUMBER).n().toInt(),
			phase = phase,
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
			doneJumping = attributes[DONE_JUMPING]?.l()?.map { it.s() } ?: emptyList(),
			// Absent when no ship waits to land, which is also how items saved before E04-S03 read.
			landingShip = attributes[LANDING_SHIP]?.s(),
			// Items saved before E05-S01 have no queue. One saved in Trading had resolved nothing, as nothing could resolve
			// then, so every good is still to resolve (BACKWARD_COMPATIBILITY.md §7).
			tradingQueue = attributes[TRADING_QUEUE]?.l()?.map { MarketGoodItem(it.m()).toMarketGood() }
				?: if (phase == TurnPhase.TRADING) MarketGood.everyOnBoard() else emptyList(),
			resolvedGoods = attributes[RESOLVED_GOODS]?.l()?.map { MarketGoodItem(it.m()).toMarketGood() } ?: emptyList(),
			// Absent before the first Resolve next, which is also how items saved before E05-S02 read: every resolution
			// finished as it started then, so none is waiting for anyone (BACKWARD_COMPATIBILITY.md §7).
			resolution = attributes[RESOLUTION]?.m()?.let { ResolutionItem(it).toResolution() }
		)
	}

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
		const val LANDING_SHIP = "landingShip"
		const val TRADING_QUEUE = "tradingQueue"
		const val RESOLVED_GOODS = "resolvedGoods"
		const val RESOLUTION = "resolution"
	}
}
