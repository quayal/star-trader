package dev.adriangrzebyk.trader.game.news

/**
 * A tip-off in play (R109): drawn in turn [drawnIn] into board [slot], public as "Tip-off #3 · CRIM 5 · effective turn 7".
 * [learnedBy] are the players who paid to learn it (private, R110); [takenBy] the player who took its opportunity (R111),
 * public only for a legal one.
 */
data class ActiveTipOff(val slot: Int, val tipOff: TipOff, val drawnIn: Int, val learnedBy: List<String>, val takenBy: String?) {

	/** The turn whose News makes it effective (R109): its draw turn plus its lead. */
	fun effectiveIn(): Int = drawnIn + tipOff.lead

	/** Whether it's due in turn [turn]'s News. */
	fun isDueIn(turn: Int): Boolean = effectiveIn() <= turn

	/** Whether [email] has learned it. */
	fun isLearnedBy(email: String): Boolean = email in learnedBy

	/** This tip-off learned by [email] too (R110); learning twice changes nothing. */
	fun learnedBy(email: String): ActiveTipOff = if (isLearnedBy(email)) this else copy(learnedBy = learnedBy + email)

	/** Whether its opportunity has been taken (R111). */
	fun isTaken(): Boolean = takenBy != null

	/** This tip-off with its opportunity taken by [email] (R111). */
	fun takenBy(email: String): ActiveTipOff = copy(takenBy = email)

	/** Whether [email] may see that its opportunity was taken (R111): everyone for a legal one, those who learned it otherwise. */
	fun showsTakenTo(email: String): Boolean = isTaken() && (tipOff.opportunity?.legal == true || isLearnedBy(email))

	/**
	 * The public line when it becomes effective (R109): an event's headline, or an opportunity's expiry, "was taken" for an
	 * illegal one that was (the taker never named) and the taker for a legal one.
	 */
	fun announcement(takerName: String?): String {
		tipOff.event?.let { return "News: ${it.headline}" }
		val terms = checkNotNull(tipOff.opportunity)
		val taken = when {
			takenBy == null -> ""
			terms.legal && takerName != null -> "; $takerName took it"
			else -> "; it was taken"
		}
		return "News: the opportunity to ${terms.description()} has expired$taken."
	}
}
