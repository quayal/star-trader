package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.game.Connections

/**
 * One tip-off in play as one player may see it (R109–R111): everyone sees its slot, need and effective turn; [learned]
 * says whether its details are theirs to see, [takenShown] whether they may see that its opportunity was taken, and
 * [needRefusal] why their connections can't learn it ("You need CRIM 5 to learn this (yours: 3)."), or null when they can.
 */
data class TipOffView(val tipOff: ActiveTipOff, val learned: Boolean, val takenShown: Boolean, val needRefusal: String?) {

	constructor(tipOff: ActiveTipOff, viewerEmail: String, connections: Connections) : this(
		tipOff,
		tipOff.isLearnedBy(viewerEmail),
		tipOff.showsTakenTo(viewerEmail),
		tipOff.tipOff.need.takeUnless { it.isMetBy(connections) }?.refusal(connections)
	)
}
