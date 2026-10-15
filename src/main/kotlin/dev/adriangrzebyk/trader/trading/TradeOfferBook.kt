package dev.adriangrzebyk.trader.trading

interface TradeOfferBook {

	fun nextInitiative(): Int

	fun add(offer: TradeOffer)

	fun all(): List<TradeOffer>

	fun forTurn(turn: Int): List<TradeOffer>
}
