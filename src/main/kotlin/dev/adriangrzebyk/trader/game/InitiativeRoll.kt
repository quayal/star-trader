package dev.adriangrzebyk.trader.game

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

	/** For the public log: "Vega Lines 40 + 11 = 51", [corporation] naming the player. */
	fun logText(corporation: String): String = "$corporation $bid + ${rolls.first()}" + (if (bonus > 0) " + $bonus" else "") + " = ${total()}"

	/** For the public log, when a tie was rolled again: "Vega Lines 9" ("Vega Lines 3, 9" over two rerolls); null with none. */
	fun tieLogText(corporation: String): String? = tieRolls().takeIf { it.isNotEmpty() }?.let { "$corporation ${it.joinToString(", ")}" }
}
