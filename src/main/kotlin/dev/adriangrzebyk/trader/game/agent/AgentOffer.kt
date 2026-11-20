package dev.adriangrzebyk.trader.game.agent

import dev.adriangrzebyk.trader.game.InvalidGameOperationException

/**
 * [sellerEmail]'s offer of their [agent] to [buyerEmail] for [price] HT (R106), made on the seller's Opportunity turn and
 * open until the buyer answers or Opportunity closes. [id] names it in the buyer's answer, so the agent's identity never
 * has to; the buyer learns who it is only by buying.
 */
data class AgentOffer(val id: Int, val sellerEmail: String, val buyerEmail: String, val agent: Agent, val price: Int) {

	/** Whether [email] may see or answer it: its seller or its buyer. */
	fun concerns(email: String): Boolean = sellerEmail == email || buyerEmail == email

	/** This offer as offer [id] of its turn. */
	fun withId(id: Int): AgentOffer = copy(id = id)

	/** Refuses anyone but its buyer answering it. */
	fun requireBuyer(email: String) {
		if (buyerEmail != email) {
			throw InvalidGameOperationException("This offer isn't for you.")
		}
	}
}
