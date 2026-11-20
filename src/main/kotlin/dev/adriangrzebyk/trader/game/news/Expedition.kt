package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.game.Ship

/**
 * A ship away on the scientific expedition (R114): off the board with its cargo, back on its owner's Opportunity turn of
 * [returnTurn] or later. Public: everyone saw it leave and its roll.
 */
data class Expedition(val ship: Ship, val returnTurn: Int) {

	/** Whether it's back in turn [turn] (R114). */
	fun isDueIn(turn: Int): Boolean = turn >= returnTurn

	companion object {
		/** R114: the expedition pays 2d6 × this on its return. */
		const val PAY_PER_PIP = 50
	}
}
