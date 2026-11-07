package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Resolution
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Resolution] as the attribute map nested in its turn's item (engineering standards §12). */
internal class ResolutionItem(val attributes: Map<String, AttributeValue>) {

	constructor(resolution: Resolution) : this(
		mapOf(
			GOOD to AttributeValue.fromM(MarketGoodItem(resolution.good).attributes),
			OFFERS to AttributeValue.fromL(resolution.offers.map { AttributeValue.fromM(OfferItem(it).attributes) }),
			DICTATOR_DECIDED to AttributeValue.fromBool(resolution.dictatorDecided)
		) + listOfNotNull(resolution.dictator?.let { DICTATOR to AttributeValue.fromS(it) })
	)

	fun toResolution(): Resolution = Resolution(
		good = MarketGoodItem(attributes.attribute(GOOD).m()).toMarketGood(),
		offers = attributes.attribute(OFFERS).l().map { OfferItem(it.m()).toOffer() },
		// Absent when the good has no Dictator at that system.
		dictator = attributes[DICTATOR]?.s(),
		dictatorDecided = attributes.attribute(DICTATOR_DECIDED).bool()
	)

	private companion object {
		const val GOOD = "good"
		const val OFFERS = "offers"
		const val DICTATOR = "dictator"
		const val DICTATOR_DECIDED = "dictatorDecided"
	}
}
