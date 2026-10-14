package pl.adriangrzebyk.trader.domain

import java.util.concurrent.atomic.AtomicInteger
import org.springframework.stereotype.Component

@Component
class TradeOfferSequence {

	private val counter = AtomicInteger(0)

	fun next(): Int = counter.incrementAndGet()
}
