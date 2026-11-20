package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentOffer
import dev.adriangrzebyk.trader.game.agent.AgentPlace
import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleReport
import dev.adriangrzebyk.trader.game.battle.Interception
import dev.adriangrzebyk.trader.game.loan.Loan
import dev.adriangrzebyk.trader.game.sabotage.Sabotage

/**
 * What one member of a game, [player], may see beyond its public state, already decided by [Game.viewFor] (R17, R29, A05,
 * engineering standards §10), so an adapter only maps it. Each field is the viewer's own or null when it isn't theirs to
 * see: their own [offers] and [bid]; [tradeMax] while the counter waits for their offer (E05-S04); [wholesaleAsk], the
 * resolution, while it asks them to trade wholesale (R37); [factoriesToCollect] while they have output to place (E06-S03);
 * [investment] while they may buy (E06-S04); [interception] when they are asked (R70); [battle] when they have a ship in
 * it or hits to place (R74–R77); [battleReport] the salvos that concern them (R78). [prices] are the game's. [loan] is their
 * loan, or in Investment what they could borrow (R90); [sabotagesOrdered] are this Opportunity's sabotages they ordered,
 * with the rolls, and [sabotagesSuffered] those on their assets, never saying who ordered them (R101), and [sabotagedWarehouse] the warehouse their sabotage hit while they choose its missing units
 * (R99); [agentOffers] the agent offers they made or were made (R106); [limpyAsk] the good Limpy may let them dictate
 * (R108); [agentPool] how many agents are left to recruit (R103).
 */
data class PlayerView(
	val player: Player,
	val offers: List<Offer>,
	val bid: InitiativeBid?,
	val tradeMax: Int?,
	val wholesaleAsk: Resolution?,
	val factoriesToCollect: List<Factory>?,
	val investment: InvestmentView?,
	val interception: Interception?,
	val battle: Battle?,
	val battleReport: BattleReport?,
	val prices: MarketPrices,
	val loan: LoanView?,
	val sabotagesOrdered: List<Sabotage>,
	val sabotagesSuffered: List<Sabotage>,
	val sabotagedWarehouse: Warehouse?,
	val agentOffers: List<AgentOfferView>,
	val limpyAsk: MarketGood?,
	val agentPool: Int,
	val opportunity: OpportunityView?
)

/** What the viewer has done this Opportunity (R97, R103): each once per Opportunity. Null outside Opportunity. */
data class OpportunityView(val sabotaged: Boolean, val recruited: Boolean, val collected: Boolean)

/**
 * The viewer's [loan] (R90–R95) in turn [turn]: open or null, whether this turn's interest is [interestPaid], and their
 * borrowing [cap] now.
 */
data class LoanView(val loan: Loan?, val turn: Int, val interestPaid: Boolean, val cap: Int)

/**
 * An agent [offer] the viewer made or may answer (R106), with where the agent stands now, [place] (null once it's gone),
 * and which [agent] it is only when the viewer is its seller: the buyer learns it by buying (R105).
 */
data class AgentOfferView(val offer: AgentOffer, val place: AgentPlace?, val agent: Agent?)

/**
 * What the viewer's Investment offers them while they may buy (E06-S04): the suggested names for a new ship
 * ([Game.freeShipNames], R62), and whether they already raised a connection (R83) or bought reputation (R87) this turn.
 */
data class InvestmentView(val freeShipNames: List<String>, val raisedConnection: Boolean, val boughtReputation: Boolean)
