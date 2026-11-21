package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Turn] as the attribute map nested in the game item (engineering standards §12). */
internal class TurnItem(val attributes: Map<String, AttributeValue>) {

	constructor(turn: Turn) : this(
		mapOf(
			NUMBER to number(turn.number),
			PHASE to AttributeValue.fromS(turn.phase.name),
			DONE_BIDDING to AttributeValue.fromL(turn.doneBidding.map { AttributeValue.fromS(it) }),
			OFFERS to AttributeValue.fromL(turn.offers.map { AttributeValue.fromM(OfferItem(it).attributes) }),
			BIDS to AttributeValue.fromL(turn.bids.map { AttributeValue.fromM(InitiativeBidItem(it).attributes) }),
			INITIATIVE to AttributeValue.fromL(turn.initiative.map { AttributeValue.fromS(it) }),
			INITIATIVE_ROLLS to AttributeValue.fromL(turn.initiativeRolls.map { AttributeValue.fromM(InitiativeRollItem(it).attributes) }),
			TURN_ORDER_PICKS to AttributeValue.fromL(turn.turnOrderPicks.map { AttributeValue.fromM(TurnOrderPickItem(it).attributes) }),
			JUMPED_SHIPS to AttributeValue.fromL(turn.jumpedShips.map { AttributeValue.fromS(it) }),
			DONE_JUMPING to AttributeValue.fromL(turn.doneJumping.map { AttributeValue.fromS(it) }),
			TRADING_QUEUE to AttributeValue.fromL(turn.tradingQueue.map { AttributeValue.fromM(MarketGoodItem(it).attributes) }),
			RESOLVED_GOODS to AttributeValue.fromL(turn.resolvedGoods.map { AttributeValue.fromM(MarketGoodItem(it).attributes) }),
			DONE_INVESTING to AttributeValue.fromL(turn.doneInvesting.map { AttributeValue.fromS(it) }),
			COLLECTED to AttributeValue.fromL(turn.collected.map { AttributeValue.fromS(it) }),
			CONNECTIONS_RAISED to strings(turn.connectionsRaised),
			REPUTATION_BOUGHT to strings(turn.reputationBought),
			DONE_OPPORTUNITY to strings(turn.doneOpportunity),
			SABOTAGES to AttributeValue.fromL(turn.sabotages.map { AttributeValue.fromM(SabotageItem(it).attributes) }),
			RECRUITED_BY to strings(turn.recruitedBy),
			AGENT_OFFERS to AttributeValue.fromL(turn.agentOffers.map { AttributeValue.fromM(AgentOfferItem(it).attributes) }),
			INTEREST_PAID to strings(turn.interestPaid),
			LIMPY_USED_BY to strings(turn.limpyUsedBy),
			DONE_NEWS to strings(turn.doneNews),
			INTERCEPTION_TRIES to AttributeValue.fromL(
				turn.interceptionTries.map {
					AttributeValue.fromM(mapOf(SHIP to AttributeValue.fromS(it.ship), INTERCEPTOR to AttributeValue.fromS(it.interceptor)))
				}
			)
		) + listOfNotNull(
			turn.landingShip?.let { LANDING_SHIP to AttributeValue.fromS(it) },
			turn.limpyAsk?.let { LIMPY_ASK to AttributeValue.fromS(it) },
			turn.resolution?.let { RESOLUTION to AttributeValue.fromM(ResolutionItem(it).attributes) },
			turn.interception?.let { INTERCEPTION to AttributeValue.fromM(InterceptionItem(it).attributes) },
			turn.battle?.let { BATTLE to AttributeValue.fromM(BattleItem(it).attributes) },
			turn.battleReport?.let { BATTLE_REPORT to AttributeValue.fromM(BattleReportItem(it).attributes) }
		)
	)

	fun toTurn(): Turn {
		// Items saved before 2026-11-20 call Jumps "SHIP_JUMPS"; the creator lets saved games lapse, so one saved mid-pick
		// reads as Jumps and its picks are not asked again.
		val phase = attributes.attribute(PHASE).s().let { if (it == LEGACY_SHIP_JUMPS) TurnPhase.JUMPS else TurnPhase.valueOf(it) }
		return Turn(
			number = attributes.attribute(NUMBER).n().toInt(),
			phase = phase,
			// Items saved before E03-S01 have no done list; nobody had signalled done then (BACKWARD_COMPATIBILITY.md §7).
			doneBidding = attributes[DONE_BIDDING]?.l()?.map { it.s() } ?: emptyList(),
			// Items saved before E03-S03 have no offers; nobody could place one then (BACKWARD_COMPATIBILITY.md §7).
			offers = attributes[OFFERS]?.l()?.map { OfferItem(it.m()).toOffer() } ?: emptyList(),
			// Items saved before E03-S05 have no bids or initiative order; nobody could bid then (BACKWARD_COMPATIBILITY.md §7).
			bids = attributes[BIDS]?.l()?.map { InitiativeBidItem(it.m()).toBid() } ?: emptyList(),
			initiative = attributes[INITIATIVE]?.l()?.map { it.s() } ?: emptyList(),
			// Items saved before E04-S01 have no picks; nobody could pick a place then (BACKWARD_COMPATIBILITY.md §7).
			turnOrderPicks = attributes[TURN_ORDER_PICKS]?.l()?.map { TurnOrderPickItem(it.m()).toPick() } ?: emptyList(),
			// Items saved before E04-S02 have no jumps; nobody could jump a ship then (BACKWARD_COMPATIBILITY.md §7).
			jumpedShips = attributes[JUMPED_SHIPS]?.l()?.map { it.s() } ?: emptyList(),
			doneJumping = attributes[DONE_JUMPING]?.l()?.map { it.s() } ?: emptyList(),
			// Absent when no ship waits to land, which is also how items saved before E04-S03 read.
			landingShip = attributes[LANDING_SHIP]?.s(),
			// Items saved before E05-S01 have no queue. One saved in Trading had resolved nothing, as nothing could resolve
			// then, so every good is still to resolve (BACKWARD_COMPATIBILITY.md §7).
			tradingQueue = attributes[TRADING_QUEUE]?.l()?.map { MarketGoodItem(it.m()).toMarketGood() }
				?: if (phase == TurnPhase.TRADING) MarketGood.everyOnBoard() else emptyList(),
			resolvedGoods = attributes[RESOLVED_GOODS]?.l()?.map { MarketGoodItem(it.m()).toMarketGood() } ?: emptyList(),
			// Absent before the first Resolve next, which is also how items saved before E05-S02 read: every resolution
			// finished as it started then, so none is waiting for anyone (BACKWARD_COMPATIBILITY.md §7).
			resolution = attributes[RESOLUTION]?.m()?.let { ResolutionItem(it).toResolution() },
			// Items saved before E06-S01 have no Investment; nobody could signal done there (BACKWARD_COMPATIBILITY.md §7).
			doneInvesting = attributes[DONE_INVESTING]?.l()?.map { it.s() } ?: emptyList(),
			// Items saved before E06-S03 have no collects; nobody had factories to collect from then (BACKWARD_COMPATIBILITY.md §7).
			collected = attributes[COLLECTED]?.l()?.map { it.s() } ?: emptyList(),
			// Absent when nobody is deciding an interception or fighting, which is also how items saved before E10 read.
			interception = attributes[INTERCEPTION]?.m()?.let { InterceptionItem(it).toInterception() },
			battle = attributes[BATTLE]?.m()?.let { BattleItem(it).toBattle() },
			// Absent until a battle round fires in the turn, which is also how items saved before 2026-11-17 read.
			battleReport = attributes[BATTLE_REPORT]?.m()?.let { BattleReportItem(it).toBattleReport() },
			// Absent from items saved before E12: nobody had raised a connection.
			connectionsRaised = attributes[CONNECTIONS_RAISED]?.l()?.map { it.s() } ?: emptyList(),
			// Absent from items saved before E13: nobody had bought reputation.
			reputationBought = attributes[REPUTATION_BOUGHT]?.l()?.map { it.s() } ?: emptyList(),
			// Absent from items saved before 2026-11-18: no battle could remember an earlier try.
			interceptionTries = attributes[INTERCEPTION_TRIES]?.l()?.map {
				BattleInterception(ship = it.m().attribute(SHIP).s(), interceptor = it.m().attribute(INTERCEPTOR).s())
			} ?: emptyList(),
			// Absent from items saved before 2026-11-18: the order was known, but not what anyone bid or rolled.
			initiativeRolls = attributes[INITIATIVE_ROLLS]?.l()?.map { InitiativeRollItem(it.m()).toInitiativeRoll() } ?: emptyList(),
			// Absent from items saved before the Opportunity phase, loans and agents (R90, R102, R103): nothing had happened yet.
			doneOpportunity = list(DONE_OPPORTUNITY),
			sabotages = attributes[SABOTAGES]?.l().orEmpty().map { SabotageItem(it.m()).toSabotage() },
			recruitedBy = list(RECRUITED_BY),
			agentOffers = attributes[AGENT_OFFERS]?.l().orEmpty().map { AgentOfferItem(it.m()).toAgentOffer() },
			interestPaid = list(INTEREST_PAID),
			limpyAsk = attributes[LIMPY_ASK]?.s(),
			limpyUsedBy = list(LIMPY_USED_BY),
			// Absent from items saved before News (R109).
			doneNews = list(DONE_NEWS)
		)
	}

	private fun list(name: String): List<String> = attributes[name]?.l().orEmpty().map { it.s() }

	private companion object {
		const val DONE_OPPORTUNITY = "doneOpportunity"
		const val SABOTAGES = "sabotages"
		const val RECRUITED_BY = "recruitedBy"
		const val AGENT_OFFERS = "agentOffers"
		const val INTEREST_PAID = "interestPaid"
		const val LIMPY_ASK = "limpyAsk"
		const val LIMPY_USED_BY = "limpyUsedBy"
		const val DONE_NEWS = "doneNews"
		const val LEGACY_SHIP_JUMPS = "SHIP_JUMPS"
		const val NUMBER = "number"
		const val PHASE = "phase"
		const val DONE_BIDDING = "doneBidding"
		const val OFFERS = "offers"
		const val BIDS = "bids"
		const val INITIATIVE = "initiative"
		const val INITIATIVE_ROLLS = "initiativeRolls"
		const val TURN_ORDER_PICKS = "turnOrderPicks"
		const val JUMPED_SHIPS = "jumpedShips"
		const val DONE_JUMPING = "doneJumping"
		const val DONE_INVESTING = "doneInvesting"
		const val COLLECTED = "collected"
		const val LANDING_SHIP = "landingShip"
		const val TRADING_QUEUE = "tradingQueue"
		const val RESOLVED_GOODS = "resolvedGoods"
		const val RESOLUTION = "resolution"
		const val INTERCEPTION = "interception"
		const val BATTLE = "battle"
		const val BATTLE_REPORT = "battleReport"
		const val CONNECTIONS_RAISED = "connectionsRaised"
		const val REPUTATION_BOUGHT = "reputationBought"
		const val INTERCEPTION_TRIES = "interceptionTries"
		const val SHIP = "ship"
		const val INTERCEPTOR = "interceptor"
	}
}
