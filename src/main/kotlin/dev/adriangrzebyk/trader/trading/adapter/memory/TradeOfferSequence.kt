package dev.adriangrzebyk.trader.trading.adapter.memory

import java.util.concurrent.atomic.AtomicInteger

class TradeOfferSequence {

	private val counter = AtomicInteger(0)

	fun next(): Int = counter.incrementAndGet()
}
