package dev.adriangrzebyk.trader.trading.adapter.memory

import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.stereotype.Component
import dev.adriangrzebyk.trader.trading.TradeOffer
import dev.adriangrzebyk.trader.trading.TradeOfferBook

@Component
class InMemoryTradeOfferBook : TradeOfferBook {

	private val initiativeSequence = TradeOfferSequence()
	private val offers = CopyOnWriteArrayList<TradeOffer>()

	override fun nextInitiative(): Int = initiativeSequence.next()

	override fun add(offer: TradeOffer) {
		offers.add(offer)
	}

	override fun all(): List<TradeOffer> = offers.toList()

	override fun forTurn(turn: Int): List<TradeOffer> = offers.filter { it.turn == turn }
}
