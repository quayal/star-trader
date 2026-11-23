package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.TakeOrder
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget

/**
 * The Opportunity phase's actions (R102): Collect, sabotage (R97–R101) and agents (R103–R106). Each runs inside
 * [GameStore.update], so it's checked against the saved game and a double press applies once (A04).
 */
class OpportunityService(private val gameStore: GameStore, private val dice: Dice) {

	/** [callerEmail] confirms where their factories' output goes, on their Opportunity turn (R47, R102). */
	fun collect(gameId: String, callerEmail: String, placements: List<OutputPlacement>): Game =
		gameStore.update(gameId) { it.collectOutput(callerEmail, placements) }

	/** [callerEmail] takes the opportunity in [slot] with [order] (R111–R114). */
	fun takeOpportunity(gameId: String, callerEmail: String, slot: Int, order: TakeOrder): Game =
		gameStore.update(gameId) { it.takeOpportunity(callerEmail, slot, order, dice) }

	/** [callerEmail] brings their expedition ship [shipName] back to [system] (R114). */
	fun returnExpedition(gameId: String, callerEmail: String, shipName: String?, system: StarSystem?): Game =
		gameStore.update(gameId) { it.returnExpedition(callerEmail, shipName, system, dice) }

	/** [callerEmail] completes the delivery aboard their ship [shipName] in its destination Port (R114, R121). */
	fun deliver(gameId: String, callerEmail: String, shipName: String?): Game = gameStore.update(gameId) { it.deliver(callerEmail, shipName) }

	/** [callerEmail] boards [groups] passenger groups onto their ship [shipName], bound for [to] (R125). */
	fun takePassengers(gameId: String, callerEmail: String, shipName: String?, to: StarSystem?, groups: Int?): Game =
		gameStore.update(gameId) { it.takePassengers(callerEmail, shipName, to, groups) }

	/** [callerEmail] sells their illegal [goods] on [system]'s black market (R112). */
	fun sellOnBlackMarket(gameId: String, callerEmail: String, system: StarSystem?, goods: SpecialGoods?): Game =
		gameStore.update(gameId) { it.sellOnBlackMarket(callerEmail, system, goods, dice) }

	/** [callerEmail] sells their furs in [system]'s Port (R112). */
	fun sellFurs(gameId: String, callerEmail: String, system: StarSystem?): Game =
		gameStore.update(gameId) { it.sellFurs(callerEmail, system) }

	/** [callerEmail] is done with opportunities (R102); after the last, Investment opens once no choice is owed. */
	fun finish(gameId: String, callerEmail: String): Game = gameStore.update(gameId) { it.finishOpportunities(callerEmail) }

	/** [callerEmail] sabotages [target], naming [blamedEmail] (R97). */
	fun sabotage(gameId: String, callerEmail: String, target: SabotageTarget, blamedEmail: String?): Game =
		gameStore.update(gameId) { it.sabotage(callerEmail, target, blamedEmail, dice) }

	/** [callerEmail] challenges [targetEmail]'s position for [goods] at [system] with their [kind] connections (R126). */
	fun challenge(gameId: String, callerEmail: String, targetEmail: String?, system: StarSystem, goods: Goods, kind: ConnectionKind): Game =
		gameStore.update(gameId) { it.challengeMarketPosition(callerEmail, targetEmail, system, goods, kind, dice) }

	/** [callerEmail] chooses the units missing from the warehouse their sabotage hit (R99). */
	fun chooseLosses(gameId: String, callerEmail: String, lots: Map<Goods, Int>): Game =
		gameStore.update(gameId) { it.chooseSabotageLosses(callerEmail, lots) }

	/** [callerEmail] places sabotage hits on their ship [shipName] (R99). */
	fun placeHits(gameId: String, callerEmail: String, shipName: String, placement: HitPlacement): Game =
		gameStore.update(gameId) { it.placeSabotageHits(callerEmail, shipName, placement) }

	/** [callerEmail] pays [payment] to recruit an agent starting at [start] (R103). */
	fun recruit(gameId: String, callerEmail: String, payment: Int?, start: AgentPosition): Game =
		gameStore.update(gameId) { it.recruitAgent(callerEmail, payment, start, dice) }

	/** [callerEmail] places their [agent], recruited this turn, at [to] (playtest, 2026-11-21). */
	fun placeNewAgent(gameId: String, callerEmail: String, agent: Agent?, to: AgentPosition): Game =
		gameStore.update(gameId) { it.placeNewAgent(callerEmail, agent, to) }

	/** [callerEmail] moves their [agent] [to] (R104). */
	fun moveAgent(gameId: String, callerEmail: String, agent: Agent?, to: AgentPosition): Game =
		gameStore.update(gameId) { it.moveAgent(callerEmail, agent, to) }

	/** [callerEmail] offers their [agent] to [buyerEmail] for [price] (R106). */
	fun offerAgent(gameId: String, callerEmail: String, agent: Agent?, buyerEmail: String?, price: Int?): Game =
		gameStore.update(gameId) { it.offerAgent(callerEmail, agent, buyerEmail, price) }

	/** [callerEmail] accepts or declines the agent offer [offerId] (R106). */
	fun answerAgentOffer(gameId: String, callerEmail: String, offerId: Int?, accept: Boolean): Game =
		gameStore.update(gameId) { it.answerAgentOffer(callerEmail, offerId, accept) }
}
