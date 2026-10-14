package pl.adriangrzebyk.trader.service

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.stereotype.Component
import pl.adriangrzebyk.trader.domain.TradeOffer

@Component
class TradeOfferBook {

	private val offers = CopyOnWriteArrayList<TradeOffer>()
	private val initiativeSequence = AtomicInteger()

	fun nextInitiative(): Int = initiativeSequence.getAndIncrement()

	fun add(offer: TradeOffer) {
		offers.add(offer)
	}

	fun all(): List<TradeOffer> = offers.toList()
}
