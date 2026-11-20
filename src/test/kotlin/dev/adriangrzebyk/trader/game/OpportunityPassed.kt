package dev.adriangrzebyk.trader.game

/**
 * [game], in Opportunity, once every player in turn order collected nothing (when they have factories) and was done
 * (R102): for tests about Investment and later, reached the way play reaches it. Unchanged outside Opportunity.
 */
class OpportunityPassed(private val game: Game) {

	fun byEveryone(): Game {
		var current = game
		while (current.turn?.phase == TurnPhase.OPPORTUNITY) {
			val email = current.turn!!.opportunist() ?: return current
			val player = current.players.first { it.email == email }
			val collected = if (player.hasFactories() && !current.turn!!.hasCollected(email)) current.collectOutput(email, emptyList()) else current
			current = collected.finishOpportunities(email)
		}
		return current
	}
}
