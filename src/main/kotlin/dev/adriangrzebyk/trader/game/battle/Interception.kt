package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.InvalidGameOperationException

/**
 * A ship that just arrived in Space at [system] (R70): [ship], [ownerEmail]'s. Every player [asked] has ships in Space
 * there and decides, all at once, which one of them tries to intercept it, if any; [answers] are their decisions, secret
 * to each until everyone has answered.
 */
data class Interception(
	val system: StarSystem,
	val ship: String,
	val ownerEmail: String,
	val asked: List<String>,
	val answers: List<InterceptionAnswer>
) {

	/** The players asked who haven't answered yet, in the order they were asked. */
	fun awaiting(): List<String> = asked.filter { email -> answers.none { it.playerEmail == email } }

	/** Whether [email] is asked: only they see the question and their own answer (R70). */
	fun asks(email: String): Boolean = email in asked

	/** Whether everyone asked has answered: the tries can be rolled. */
	fun isDecided(): Boolean = awaiting().isEmpty()

	/** [email]'s answer, if they gave one: only they may see it before the tries are rolled. */
	fun answerOf(email: String): InterceptionAnswer? = answers.firstOrNull { it.playerEmail == email }

	/** This interception with [answer]; refused for a player who isn't asked or has already answered. */
	fun answer(answer: InterceptionAnswer): Interception {
		if (answer.playerEmail !in asked) {
			throw InvalidGameOperationException("You have no ship in Space at ${system.displayName} to intercept $ship with.")
		}
		if (answerOf(answer.playerEmail) != null) {
			throw InvalidGameOperationException("You have already decided about $ship.")
		}
		return copy(answers = answers + answer)
	}

	/** Every try, as player and ship, in the order the players were asked: at most one per player (R70). */
	fun tries(): List<Pair<String, String>> = asked.mapNotNull { email -> answerOf(email)?.ship?.let { email to it } }
}

/** [playerEmail]'s decision on an interception (R70): the one [ship] of theirs that tries, or null for none. */
data class InterceptionAnswer(val playerEmail: String, val ship: String?)
