package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.StarSystem

/** One good at one system: what a Trading resolution is about (R25, R36). Public, like the board. */
data class MarketGood(val system: StarSystem, val goods: Goods) {

	/** For the public log and messages: "Mu Herculis ISO". */
	fun label(): String = "${system.displayName} ${goods.name}"

	companion object {
		/** R36, S01-2: every good at every system, by system in map order, then good. */
		fun everyOnBoard(): List<MarketGood> =
			StarSystem.entries.flatMap { system -> Goods.entries.filter { it in system.goods }.map { MarketGood(system, it) } }
	}
}
