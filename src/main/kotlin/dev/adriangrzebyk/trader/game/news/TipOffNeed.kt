package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.game.ConnectionKind
import dev.adriangrzebyk.trader.game.Connections

/** What a player needs to learn a tip-off (R110): a [level] of [kind] connections, public as "CRIM 5". */
data class TipOffNeed(val kind: ConnectionKind, val level: Int) {

	/** Whether [connections] reach this level now. */
	fun isMetBy(connections: Connections): Boolean = connections.of(kind) >= level

	/** "CRIM 5", "BUSS 10", "POL 4": how the creator's table and the screens write it. */
	fun text(): String = "${ABBREVIATIONS.getValue(kind)} $level"

	/** The refusal when [connections] fall short: "You need CRIM 5 to learn this (yours: 3)." */
	fun refusal(connections: Connections): String = "You need ${text()} to learn this (yours: ${connections.of(kind)})."

	companion object {
		private val ABBREVIATIONS = mapOf(ConnectionKind.POLITICAL to "POL", ConnectionKind.BUSINESS to "BUSS", ConnectionKind.CRIMINAL to "CRIM")

		fun pol(level: Int) = TipOffNeed(ConnectionKind.POLITICAL, level)

		fun buss(level: Int) = TipOffNeed(ConnectionKind.BUSINESS, level)

		fun crim(level: Int) = TipOffNeed(ConnectionKind.CRIMINAL, level)
	}
}
