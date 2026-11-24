package dev.adriangrzebyk.trader.game.control

/**
 * A reputation control that failed its first roll (R129), waiting for another player to back [subject]: every email in
 * [asked] has yet to answer. Once someone backs, [backer] is set and nobody else is asked. Public, so everyone sees what the
 * game waits for (D09); who declined isn't said.
 */
data class ControlAudit(val subject: String, val asked: List<String>, val backer: String?) {

	/** Whether [email] is still being asked to back [subject]. */
	fun asks(email: String): Boolean = backer == null && email in asked

	/** This audit once [email] answered: backing makes them the backer and ends the asking; declining only takes them off the list. */
	fun answered(email: String, back: Boolean): ControlAudit =
		if (back) copy(asked = emptyList(), backer = email) else copy(asked = asked - email)

	/** Whether nobody is left to ask, so the second roll can be made. */
	fun isDecided(): Boolean = asked.isEmpty()
}
