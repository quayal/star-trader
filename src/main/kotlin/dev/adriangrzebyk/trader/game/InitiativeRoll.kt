package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.agent.Agent

/**
 * What [playerEmail] bid for this turn's initiative and rolled (R27, corrected 2026-11-18: bid + 2d6). [rolls] holds
 * the 2d6 roll first, then any roll made to break a tie, in order. Public once Bidding closes: every player is told what
 * each player paid and rolled (creator, 2026-11-18).
 */
data class InitiativeRoll(val playerEmail: String, val bid: Int, val bonus: Int, val rolls: List<Int>) {

	/** The bid plus [bonus] (Wolf, R108) plus the first roll: what sets the order before any tie is rolled again. */
	fun total(): Int = bid + bonus + rolls.first()

	/** The rolls made only to break a tie, empty when there was none. */
	fun tieRolls(): List<Int> = rolls.drop(1)

	/** The roll made last: what orders players who are still tied. */
	fun latestRoll(): Int = rolls.last()

	/** This roll with [roll] made to break a tie. */
	fun withTieRoll(roll: Int): InitiativeRoll = copy(rolls = rolls + roll)

	/**
	 * Who gave [bonus]: Wolf, the only initiative bonus (R108), named wherever the roll is shown, which reveals him (R105,
	 * playtest 2026-11-20); null with no bonus.
	 */
	fun bonusFrom(): String? = if (bonus > 0) Agent.WOLF.displayName else null

	/** For the public log: "Vega Lines 40 + 11 = 51", or "Vega Lines 5 + 3 + Wolf 4 = 12"; [corporation] names the player. */
	fun logText(corporation: String): String =
		"$corporation $bid + ${rolls.first()}" + (bonusFrom()?.let { " + $it $bonus" } ?: "") + " = ${total()}"

	/** For the public log, when a tie was rolled again: "Vega Lines 9" ("Vega Lines 3, 9" over two rerolls); null with none. */
	fun tieLogText(corporation: String): String? = tieRolls().takeIf { it.isNotEmpty() }?.let { "$corporation ${it.joinToString(", ")}" }
}
