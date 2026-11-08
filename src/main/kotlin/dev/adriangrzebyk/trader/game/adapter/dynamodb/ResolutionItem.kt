package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Resolution
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Resolution] as the attribute map nested in its turn's item (engineering standards §12). */
internal class ResolutionItem(val attributes: Map<String, AttributeValue>) {

	constructor(resolution: Resolution) : this(
		mapOf(
			GOOD to AttributeValue.fromM(MarketGoodItem(resolution.good).attributes),
			OFFERS to AttributeValue.fromL(resolution.offers.map { AttributeValue.fromM(OfferItem(it).attributes) }),
			DICTATOR_DECIDED to AttributeValue.fromBool(resolution.dictatorDecided),
			TRADES to AttributeValue.fromL(resolution.trades.map { AttributeValue.fromM(TradeItem(it).attributes) }),
			INITIATIVE to AttributeValue.fromL(resolution.initiative.map { AttributeValue.fromS(it) }),
			START_PRICE to number(resolution.startPrice),
			WHOLESALERS to AttributeValue.fromL(resolution.wholesalers.map { AttributeValue.fromS(it) }),
			WHOLESALES to AttributeValue.fromL(resolution.wholesales.map { AttributeValue.fromM(WholesaleItem(it).attributes) })
		) + listOfNotNull(
			resolution.dictator?.let { DICTATOR to AttributeValue.fromS(it) },
			resolution.startingCounter?.let { STARTING_COUNTER to number(it) }
		)
	)

	fun toResolution(): Resolution {
		val good = MarketGoodItem(attributes.attribute(GOOD).m()).toMarketGood()
		return Resolution(
			good = good,
			// Absent on resolutions saved before E05-S05, when every good's price was the board's.
			startPrice = attributes[START_PRICE]?.n()?.toInt() ?: good.boardPrice(),
			offers = attributes.attribute(OFFERS).l().map { OfferItem(it.m()).toOffer() },
			// Absent when the good has no Dictator at that system.
			dictator = attributes[DICTATOR]?.s(),
			dictatorDecided = attributes.attribute(DICTATOR_DECIDED).bool(),
			// Absent until the counter starts, and on every resolution saved before E05-S03, which finished without one.
			startingCounter = attributes[STARTING_COUNTER]?.n()?.toInt(),
			// Absent on resolutions saved before E05-S03, when nothing traded.
			trades = attributes[TRADES]?.l()?.map { TradeItem(it.m()).toTrade() } ?: emptyList(),
			// Absent on resolutions saved before E05-S04, whose counter had run to its end, so no tie is left to break.
			initiative = attributes[INITIATIVE]?.l()?.map { it.s() } ?: emptyList(),
			// Absent on resolutions saved before E05-S05, which asked nobody to trade wholesale.
			wholesalers = attributes[WHOLESALERS]?.l()?.map { it.s() } ?: emptyList(),
			wholesales = attributes[WHOLESALES]?.l()?.map { WholesaleItem(it.m()).toWholesale() } ?: emptyList()
		)
	}

	private companion object {
		const val GOOD = "good"
		const val OFFERS = "offers"
		const val DICTATOR = "dictator"
		const val DICTATOR_DECIDED = "dictatorDecided"
		const val STARTING_COUNTER = "startingCounter"
		const val TRADES = "trades"
		const val INITIATIVE = "initiative"
		const val START_PRICE = "startPrice"
		const val WHOLESALERS = "wholesalers"
		const val WHOLESALES = "wholesales"
	}
}
