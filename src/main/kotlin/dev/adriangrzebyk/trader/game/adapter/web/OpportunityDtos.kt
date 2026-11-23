package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.AgentOfferView
import dev.adriangrzebyk.trader.game.ConnectionKind
import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.game.LoanView
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.OpportunityView
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentCount
import dev.adriangrzebyk.trader.game.agent.AgentPlace
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import dev.adriangrzebyk.trader.game.loan.Loan
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import dev.adriangrzebyk.trader.game.sabotage.Sabotage
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget
import java.math.BigDecimal

private fun whole(value: BigDecimal?): Int? = value?.let { runCatching { it.intValueExact() }.getOrNull() }

/** How many of a player's agents are on their own at a [system]'s [location] (R105): public, no names. */
data class AgentCountResponseDto(val system: String, val systemName: String, val location: String, val count: Int) {

	constructor(count: AgentCount) : this(count.place.system.name, count.place.system.displayName, count.place.location.name, count.count)
}

/**
 * The viewer's loan (R90–R95), private (R29): [loan] when one is open, with [turnsLeft], whether this turn's interest is
 * [interestDue] and [interestPaid], whether it [fallsDue] when this Investment closes, and what [repayment] costs; and
 * always their borrowing [cap] and the [terms] they may borrow over, with what each divides (amount − reputation) by
 * and the [minInterest], so the form can show both interests as the amount is typed (R91).
 */
data class YourLoanResponseDto(
	val cap: Int,
	val loan: OpenLoanResponseDto?,
	val interestDue: Boolean,
	val interestPaid: Boolean,
	val terms: List<LoanTermResponseDto>,
	val minInterest: Int
) {

	constructor(view: LoanView) : this(
		view.cap,
		view.loan?.let { OpenLoanResponseDto(it, view.turn, view.interestPaid) },
		view.loan?.isInterestDue(view.turn) == true,
		view.interestPaid,
		LoanTerm.entries.map { LoanTermResponseDto(it) },
		Loan.MIN_INTEREST
	)
}

/** A loan term (R90, R91): its [turns] and the [divisor] of (amount − reputation) that gives the interest a turn. */
data class LoanTermResponseDto(val turns: Int, val divisor: Int) {

	constructor(term: LoanTerm) : this(term.turns, term.divisor)
}

/** An open loan: what is still owed, the fixed interest, its term, the turns of interest left, and what repaying costs now. */
data class OpenLoanResponseDto(
	val amount: Int,
	val interest: Int,
	val term: Int,
	val turnsLeft: Int,
	val fallsDue: Boolean,
	val repayment: Int
) {

	constructor(loan: Loan, turn: Int, interestPaid: Boolean) : this(
		loan.amount,
		loan.interest,
		loan.term.turns,
		loan.turnsLeft(turn),
		loan.fallsDueIn(turn),
		loan.repayment(loan.isInterestDue(turn) && !interestPaid)
	)
}

/** `{ amount, term }`: borrow [amount] HT over [term] turns (4 or 8) (R90). */
data class TakeLoanRequestDto(val amount: BigDecimal? = null, val term: BigDecimal? = null) {

	fun wholeAmount(): Int? = whole(amount)

	fun loanTerm(): LoanTerm? = LoanTerm.ofTurns(whole(term))
}

/** What the viewer's factory would fetch per unit sold now (R96), private like money. */
data class FactorySaleResponseDto(val system: String, val goods: String, val units: Int, val pricePerUnit: Int) {

	constructor(factory: Factory, prices: MarketPrices) : this(
		factory.system.name,
		factory.goods.name,
		factory.size,
		factory.salePricePerUnit(prices)
	)
}

/** `{ system, goods, units }`: sell [units] of the factory for [goods] at [system] (R96). */
data class SellFactoryRequestDto(val system: String? = null, val goods: String? = null, val units: BigDecimal? = null) {

	fun starSystem(): StarSystem? = StarSystem.entries.firstOrNull { it.name == system }

	fun goodsType(): Goods? = Goods.entries.firstOrNull { it.name == goods }

	fun wholeUnits(): Int? = whole(units)
}

/**
 * One of the viewer's own agents (R105): its name, effect and where it is, aboard [ship] or on its own at [system] [location].
 * [placeSystemName] and [placeLocation] say where it stands either way, its ship's place when aboard (playtest, 2026-11-20:
 * "player should have info where their agents are"). [placeable] says its player may still choose where it starts, having
 * just recruited it (playtest, 2026-11-21).
 */
data class YourAgentResponseDto(
	val agent: String,
	val name: String,
	val effect: String,
	val ship: String?,
	val system: String?,
	val location: String?,
	val placeSystemName: String,
	val placeLocation: String,
	val placeable: Boolean
) {

	constructor(recruited: RecruitedAgent, place: AgentPlace, turn: Int) : this(
		recruited.agent.name,
		recruited.agent.displayName,
		recruited.agent.effect,
		(recruited.position as? AgentPosition.Aboard)?.shipName,
		(recruited.position as? AgentPosition.OnOwn)?.system?.name,
		(recruited.position as? AgentPosition.OnOwn)?.location?.name,
		place.system.displayName,
		place.location.name,
		recruited.isPlaceableIn(turn)
	)
}

/** What the viewer did and may do this Opportunity (R97, R103, R126). */
data class YourOpportunityResponseDto(val sabotaged: Boolean, val recruited: Boolean, val challenged: Boolean, val collected: Boolean) {

	constructor(view: OpportunityView) : this(view.sabotaged, view.recruited, view.challenged, view.collected)
}

/**
 * A sabotage the viewer ordered (R101): the target, the blamed player, the cost, both rolls with their modifiers and
 * totals, the effect, what they lose, and whether it still waits for their warehouse choice ([choosing]).
 */
data class YourSabotageResponseDto(
	val targetPlayerId: String,
	val kind: String,
	val system: String,
	val systemName: String,
	val ship: String?,
	val goods: String?,
	val blamedId: String,
	val cost: Int,
	val foiledBy: String?,
	val effectRoll: Int?,
	val effectModifier: Int,
	val effectTotal: Int?,
	val effect: Int,
	val reputationRoll: Int?,
	val reputationModifier: Int,
	val reputationTotal: Int?,
	val reputationLoss: Int,
	val blamed: Boolean,
	val choosing: Boolean,
	val lost: Map<String, Int>
) {

	constructor(sabotage: Sabotage, ids: PlayerIds) : this(
		ids.of(sabotage.target.ownerEmail),
		sabotage.target.kind(),
		sabotage.target.system.name,
		sabotage.target.system.displayName,
		(sabotage.target as? SabotageTarget.Ship)?.name,
		(sabotage.target as? SabotageTarget.Factory)?.goods?.name,
		ids.of(sabotage.blamedEmail),
		sabotage.cost,
		sabotage.foiledBy,
		sabotage.effectRoll,
		sabotage.effectModifier,
		sabotage.effectTotal(),
		sabotage.effect(),
		sabotage.reputationRoll,
		sabotage.reputationModifier,
		sabotage.reputationTotal(),
		sabotage.saboteurLoss(),
		sabotage.blamesInnocent(),
		sabotage.awaitsLossChoiceBy(sabotage.saboteurEmail),
		sabotage.lost.entries.associate { it.key.name to it.value }
	)
}

/** A sabotage on the viewer's assets (R101): what was hit and what it did, never who ordered it; [awaitingHits] for a ship's hits to place. */
data class SufferedSabotageResponseDto(
	val kind: String,
	val system: String,
	val systemName: String,
	val ship: String?,
	val goods: String?,
	val effect: Int,
	val foiled: Boolean,
	val awaitingHits: Boolean,
	val lost: Map<String, Int>
) {

	constructor(sabotage: Sabotage) : this(
		sabotage.target.kind(),
		sabotage.target.system.name,
		sabotage.target.system.displayName,
		(sabotage.target as? SabotageTarget.Ship)?.name,
		(sabotage.target as? SabotageTarget.Factory)?.goods?.name,
		sabotage.effect(),
		sabotage.foiledBy != null,
		sabotage.awaitsHits(),
		sabotage.lost.entries.associate { it.key.name to it.value }
	)
}

/** `{ targetPlayerId, kind, system, ship, goods, blamedId }`: sabotage another player's ship, warehouse or factory (R97). */
data class SabotageRequestDto(
	val targetPlayerId: String? = null,
	val kind: String? = null,
	val system: String? = null,
	val ship: String? = null,
	val goods: String? = null,
	val blamedId: String? = null
) {

	/** The target for [ownerEmail], or null when the kind, system or goods isn't one. */
	fun target(ownerEmail: String): SabotageTarget? {
		val at = StarSystem.entries.firstOrNull { it.name == system } ?: return null
		return when (kind) {
			"SHIP" -> ship?.let { SabotageTarget.Ship(ownerEmail, at, it) }
			"WAREHOUSE" -> SabotageTarget.Warehouse(ownerEmail, at)
			"FACTORY" -> Goods.entries.firstOrNull { it.name == goods }?.let { SabotageTarget.Factory(ownerEmail, at, it) }
			else -> null
		}
	}
}

/** `{ lots: { GOODS: units } }`: the units the saboteur takes from the warehouse (R99). */
data class SabotageLossesRequestDto(val lots: Map<String, BigDecimal>? = null) {

	/** The lots, or null when a good is unknown or a count isn't whole. */
	fun goodsLots(): Map<Goods, Int>? = lots.orEmpty().entries.associate { (goods, units) ->
		(Goods.entries.firstOrNull { it.name == goods } ?: return null) to (whole(units) ?: return null)
	}
}

/**
 * Where an agent goes or starts (R103, R104): aboard [ship], or on its own at [system]'s [location] (`PORT` or `PLANET`).
 * [amount] is what a recruiter pays; [agent] the agent moved.
 */
data class AgentRequestDto(
	val amount: BigDecimal? = null,
	val agent: String? = null,
	val ship: String? = null,
	val system: String? = null,
	val location: String? = null
) {

	fun wholeAmount(): Int? = whole(amount)

	fun agentNamed(): Agent? = Agent.entries.firstOrNull { it.name == agent }

	/** The position, or null when it's neither a ship nor a Port or Planet at a system. */
	fun position(): AgentPosition? {
		ship?.let { return AgentPosition.Aboard(it) }
		val at = StarSystem.entries.firstOrNull { it.name == system } ?: return null
		val where = ShipLocation.entries.firstOrNull { it.name == location }?.takeIf { it == ShipLocation.PORT || it == ShipLocation.PLANET }
			?: return null
		return AgentPosition.OnOwn(at, where)
	}
}

/** `{ agent, buyerId, price }`: offer one of your agents to another player (R106). */
data class OfferAgentRequestDto(val agent: String? = null, val buyerId: String? = null, val price: BigDecimal? = null) {

	fun agentNamed(): Agent? = Agent.entries.firstOrNull { it.name == agent }

	fun wholePrice(): Int? = whole(price)
}

/**
 * An agent offer the viewer made or may answer (R106): its [id], seller and buyer, the [price], where the agent stands,
 * and its [agent] name only for the seller, who knows it.
 */
data class AgentOfferResponseDto(
	val id: Int,
	val sellerId: String,
	val buyerId: String,
	val price: Int,
	val system: String?,
	val systemName: String?,
	val location: String?,
	val agent: String?
) {

	constructor(view: AgentOfferView, ids: PlayerIds) : this(
		view.offer.id,
		ids.of(view.offer.sellerEmail),
		ids.of(view.offer.buyerEmail),
		view.offer.price,
		view.place?.system?.name,
		view.place?.system?.displayName,
		view.place?.location?.name,
		view.agent?.displayName
	)
}

/** `{ act }`: act as Dictator through Limpy, or pass (R108). */
data class LimpyRequestDto(val act: Boolean? = null)

/** `{ targetPlayerId, system, goods, kind }`: challenge a market position of another player with `POLITICAL` or `BUSINESS` connections (R126). */
data class ChallengeRequestDto(
	val targetPlayerId: String? = null,
	val system: String? = null,
	val goods: String? = null,
	val kind: String? = null
) {

	/** The system named, or null when it isn't one. */
	fun starSystem(): StarSystem? = StarSystem.entries.firstOrNull { it.name == system }

	/** The goods named, or null when they aren't any. */
	fun tradedGoods(): Goods? = Goods.entries.firstOrNull { it.name == goods }

	/** The connection named, or null when it isn't one. */
	fun connectionKind(): ConnectionKind? = ConnectionKind.entries.firstOrNull { it.name == kind }
}
