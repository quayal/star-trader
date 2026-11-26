package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.MarketGood
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** One good's price at one system, as an entry of its game's price list (R35, engineering standards §12). */
internal class MarketPriceItem(val attributes: Map<String, AttributeValue>) {

	constructor(good: MarketGood, price: Int, change: Int) :
		this(MarketGoodItem(good).attributes + (PRICE to number(price)) + (CHANGE to number(change)))

	fun good(): MarketGood = MarketGoodItem(attributes).toMarketGood()

	fun price(): Int = attributes.attribute(PRICE).n().toInt()

	/** The price's move when it last resolved; absent from items saved before it was kept (Bidding screen, R140): none. */
	fun change(): Int = attributes[CHANGE]?.n()?.toInt() ?: 0

	private companion object {
		const val PRICE = "price"
		const val CHANGE = "change"
	}
}
