package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem

/**
 * A trade that took place, kept on its log entry so a player can list their own trades (creator, 2026-11-18): [playerEmail]
 * bought or sold ([type]) [units] of [goods] at [system] for [price] HT each. Public, like every trade result (R18).
 */
data class TradeRecord(
	val playerEmail: String,
	val system: StarSystem,
	val goods: Goods,
	val type: OfferType,
	val units: Int,
	val price: Int
) {

	/** Whether this trade is [offer]'s player trading its good its way: the offer traded. */
	fun isFor(offer: Offer): Boolean = playerEmail == offer.playerEmail && system == offer.system && goods == offer.goods && type == offer.type
}
