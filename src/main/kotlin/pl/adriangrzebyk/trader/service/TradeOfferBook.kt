package pl.adriangrzebyk.trader.service

import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.stereotype.Component
import pl.adriangrzebyk.trader.domain.TradeOffer
import pl.adriangrzebyk.trader.domain.TradeOfferSequence

@Component
class TradeOfferBook(private val initiativeSequence: TradeOfferSequence) {

	private val offers = CopyOnWriteArrayList<TradeOffer>()

	fun nextInitiative(): Int = initiativeSequence.next()

	fun add(offer: TradeOffer) {
		offers.add(offer)
	}

	fun all(): List<TradeOffer> = offers.toList()

	fun forTurn(turn: Int): List<TradeOffer> = offers.filter { it.turn == turn }
}
