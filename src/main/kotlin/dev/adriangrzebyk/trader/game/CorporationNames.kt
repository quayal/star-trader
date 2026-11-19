package dev.adriangrzebyk.trader.game

import java.util.Locale

/**
 * Each player's corporation name by their email, which is how the public log and the "waiting for" messages name players
 * (R29: never by email). [Game.corporationNames] builds it; a part that writes a log line takes this value, not the
 * players (engineering standards §13 row 5).
 */
class CorporationNames(private val byEmail: Map<String, String>) {

	/** The corporation of the player [email]. */
	fun of(email: String): String = byEmail.getValue(email)

	/** [emails]' corporations for a sentence: "Orion Freight", "Vega Lines and Kessler Cargo", "A, B and C". */
	fun list(emails: List<String>): String {
		val names = emails.map { of(it) }
		return if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
	}

	/**
	 * S08-5: "Game over: Vega Lines wins with 2,310 HT", or for a shared win (S08-4) "Game over: Vega Lines and Kessler
	 * Cargo win with 2,310 HT", [winners] each holding [money].
	 */
	fun winText(winners: List<String>, money: Int): String {
		val verb = if (winners.size == 1) "wins" else "win"
		return "Game over: ${list(winners)} $verb with ${String.format(Locale.US, "%,d", money)} HT"
	}
}
