package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.AwaitedTrade
import dev.adriangrzebyk.trader.game.CargoHolder
import dev.adriangrzebyk.trader.game.ConnectionKind
import dev.adriangrzebyk.trader.game.Connections
import dev.adriangrzebyk.trader.game.CrewClass
import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameResult
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.InitiativeRoll
import dev.adriangrzebyk.trader.game.InterceptionRoll
import dev.adriangrzebyk.trader.game.JumpChance
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.Notice
import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.game.OutputPlacement
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.PlayerView
import dev.adriangrzebyk.trader.game.PositionRoll
import dev.adriangrzebyk.trader.game.ProductionCost
import dev.adriangrzebyk.trader.game.Reputation
import dev.adriangrzebyk.trader.game.Resolution
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLoad
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.Trade
import dev.adriangrzebyk.trader.game.TradeRecord
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.Warehouse
import dev.adriangrzebyk.trader.game.Wholesale
import dev.adriangrzebyk.trader.game.battle.BattleSummarySection
import dev.adriangrzebyk.trader.game.ledger.MoneyMovement
import dev.adriangrzebyk.trader.game.news.News
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TipOffView
import dev.adriangrzebyk.trader.game.smuggling.LandingOdds
import java.math.BigDecimal

// BigDecimal, not Int: Jackson would otherwise round 2.5 down to 2 and create a game the player didn't ask for.
data class CreateGameRequestDto(val turns: BigDecimal? = null) {

	/** The length as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholeTurns(): Int? = turns?.let { runCatching { it.intValueExact() }.getOrNull() }
}

/**
 * One player's view of a game: public state for everyone, plus [you] with what only the viewer may see (R29, their
 * ships' cargo, S02-7, and their own offers, R17). [board] is the six systems with this game's prices, which Trading
 * moves (E05-S05, R35). [result] is null until the game is over, then every player's final money and the winners for
 * everyone (E05-S08, R39: the one exception to R29). [log] is the whole log from its [logOffset]th entry on: 0, so all of
 * it, unless the poll asked for the entries after the ones it has.
 */
data class GameResponseDto(
	val id: String,
	val turns: Int,
	val status: String,
	val creatorId: String,
	val players: List<PlayerResponseDto>,
	val turn: TurnResponseDto?,
	val log: List<LogEntryResponseDto>,
	val logOffset: Int,
	val board: List<SystemResponseDto>,
	val result: GameResultResponseDto?,
	val you: YouResponseDto?,
	val conditions: ConditionsResponseDto?
) {

	/**
	 * Only [viewerEmail]'s own money goes into the response; null [you] for a viewer who isn't a player. [logAfter] is how
	 * many log entries the viewer already has ([Game.logAfter]); 0 sends them all.
	 */
	constructor(game: Game, viewerEmail: String, logAfter: Int = 0) : this(game, viewerEmail, logAfter, PlayerIds(game))

	/** As above, with the game's [ids]: every player is named by their id, never their email ([PlayerIds]). */
	private constructor(game: Game, viewerEmail: String, logAfter: Int, ids: PlayerIds) : this(
		game.id,
		game.turns,
		game.status.name,
		ids.of(game.creatorEmail),
		game.players.map { PlayerResponseDto(it, ids) },
		game.turn?.let { TurnResponseDto(it, ids, game.players.flatMap { player -> player.ships }) },
		game.logAfter(logAfter).map { LogEntryResponseDto(it, ids) },
		game.log.size - game.logAfter(logAfter).size,
		StarSystem.entries.map { SystemResponseDto(it, game.prices) },
		game.result()?.let { GameResultResponseDto(it, ids) },
		game.viewFor(viewerEmail)?.let { YouResponseDto(it, ids) },
		game.turn?.let { ConditionsResponseDto(game.news, it.number) }
	)
}

/**
 * What this turn's events changed (R115), public: [jumpModifier] on every jump's target (E1), whether [portsClosed] (E10),
 * the [securityChange] on every Planet and [policeChange] on every system (E3, E10, E12), and whether ships are for sale
 * ([shipsForSale], E3).
 */
data class ConditionsResponseDto(
	val jumpModifier: Int,
	val portsClosed: Boolean,
	val securityChange: Int,
	val policeChange: Int,
	val shipsForSale: Boolean
) {

	constructor(news: News, turn: Int) : this(
		news.conditions.jumpModifierIn(turn),
		news.conditions.portsClosedIn(turn),
		news.conditions.securityChangeIn(turn),
		news.conditions.policeChangeIn(turn),
		news.shipsForSaleIn(turn)
	)
}

/**
 * A finished game's result (E05-S08-2, S08-3), public: every player, most money first, and the ids of the winners,
 * several when they tie on money (S08-4). Only built for a finished game: until then money is private (R29).
 */
data class GameResultResponseDto(val standings: List<StandingResponseDto>, val winners: List<String>) {

	constructor(result: GameResult, ids: PlayerIds) : this(
		result.standings.map { StandingResponseDto(it, ids) },
		result.winners.map { ids.of(it.email) }
	)
}

/** One player's final money (R39: cash only), shown to everyone once the game is over (S08-3). */
data class StandingResponseDto(val id: String, val corporationName: String, val money: Int) {

	constructor(player: Player, ids: PlayerIds) : this(ids.of(player.email), player.corporationName, player.finalMoney())
}

/**
 * Public: no money and no cargo here, ever (R29, S02-7), and no email: a player is named by their [id] ([PlayerIds]). Market positions are public (R28), and so are warehouses and
 * factories (R50, E06-S02-2), and their [connections] and the starting [connectionPoints] they still have to place (R81,
 * R82, E12-S01-10), and their [reputation], 0 to 40 (R85, E13-S01-1), with what the end of the turn adds to it
 * ([reputationRecovery], R86) and the bonus it pays then ([reputationBonus], R88), both derived from it (playtest, 2026-11-20).
 */
data class PlayerResponseDto(
	val id: String,
	val name: String,
	val corporationName: String,
	val ships: List<ShipResponseDto>,
	val marketPositions: List<MarketPositionResponseDto>,
	val warehouses: List<WarehouseResponseDto>,
	val factories: List<FactoryResponseDto>,
	val connections: ConnectionsResponseDto,
	val connectionPoints: Int,
	val reputation: Int,
	val reputationRecovery: Int,
	val reputationBonus: Int,
	val agentsOnOwn: List<AgentCountResponseDto>
) {

	constructor(player: Player, ids: PlayerIds) : this(
		ids.of(player.email),
		player.name,
		player.corporationName,
		player.ships.map { ShipResponseDto(it, player.agentsAboard(it.name)) },
		player.marketPositions.map { MarketPositionResponseDto(it) },
		player.warehouses.map { WarehouseResponseDto(it) },
		player.factories.map { FactoryResponseDto(it) },
		ConnectionsResponseDto(player.connections),
		player.connectionPoints,
		player.reputation.value,
		player.reputationRecovery(),
		player.reputationBonus(),
		player.agentsOnOwn().map { AgentCountResponseDto(it) }
	)
}

/** A player's connections, each from 0 to 10 (R81): public. */
data class ConnectionsResponseDto(val political: Int, val business: Int, val criminal: Int) {

	constructor(connections: Connections) : this(connections.political, connections.business, connections.criminal)
}

/** The starting connection points to place (R82, E12-S01-2); each is a [BigDecimal] so 2.5 reaches a message instead of being rounded. */
data class PlaceConnectionsRequestDto(
	val political: BigDecimal? = null,
	val business: BigDecimal? = null,
	val criminal: BigDecimal? = null
) {

	fun wholePolitical(): Int? = whole(political)

	fun wholeBusiness(): Int? = whole(business)

	fun wholeCriminal(): Int? = whole(criminal)

	private fun whole(value: BigDecimal?): Int? = value?.let { runCatching { it.intValueExact() }.getOrNull() }
}

/** The connection to raise by one point (R83, E12-S02): a `ConnectionKind` constant. */
data class RaiseConnectionRequestDto(val kind: String? = null) {

	fun connectionKind(): ConnectionKind? = ConnectionKind.entries.firstOrNull { it.name == kind }
}

/** A warehouse's system (a `StarSystem` constant) and its capacity in blocks and cargo spaces (R45). */
data class WarehouseResponseDto(val system: String, val blocks: Int, val cargoSpaces: Int) {

	constructor(warehouse: Warehouse) : this(warehouse.system.name, warehouse.blocks, warehouse.cargoSpaces())
}

/** A factory's system and good (constants) and its size, the units it produces a turn (R46). */
data class FactoryResponseDto(val system: String, val goods: String, val size: Int, val disabledTurns: Int) {

	constructor(factory: Factory) : this(factory.system.name, factory.goods.name, factory.size, factory.disabledTurns)
}

/** Where a ship is, which every player may see; what it carries is only in [YourShipResponseDto]. */
data class ShipResponseDto(val name: String, val system: String, val systemName: String, val location: String, val agents: Int) {

	/** [agents] is how many agents ride it: public, unlike who they are (R105). */
	constructor(ship: Ship, agents: Int) : this(ship.name, ship.system.name, ship.system.displayName, ship.location.name, agents)
}

data class MarketPositionResponseDto(val system: String, val goods: String, val tier: String) {

	constructor(position: MarketPosition) : this(position.system.name, position.goods.name, position.tier.name)
}

/**
 * One system of the board: its R31 location statistics and its market at this game's [MarketPrices] (D07, D08, R35),
 * and the goods a factory here can make, empty where none can (R46, E06-S02-3). [jumpChances] is the base chance of a
 * jump from here to each other system, keyed by `StarSystem` constant (R68): public, the same for everyone, before a
 * ship's crew and Safe jump modifiers. [shipyard] is whether ships can be bought, refitted and repaired here (R60).
 */
data class SystemResponseDto(
	val id: String,
	val name: String,
	val portClass: Int,
	val legalSystem: Int,
	val police: Int,
	val security: Int,
	val market: List<MarketEntryResponseDto>,
	val producibleGoods: List<String>,
	val jumpChances: Map<String, Int>,
	val shipyard: Boolean
) {

	constructor(system: StarSystem, prices: MarketPrices) : this(
		system.name,
		system.displayName,
		system.locations.portClass,
		system.locations.legalSystem,
		system.locations.police,
		system.locations.security,
		system.goods.sortedBy { it.name }.map { MarketEntryResponseDto(MarketGood(system, it), prices) },
		system.producibleGoods.map { it.name }.sorted(),
		StarSystem.entries.mapNotNull { to -> system.baseJumpChanceTo(to)?.let { to.name to it } }.toMap(),
		system.hasShipyard()
	)
}

/** [price] is this game's (R35); [indicator] is the S/D number, which never changes; the client steps it into a market label (D08). */
data class MarketEntryResponseDto(val goods: String, val price: Int, val indicator: Int) {

	constructor(good: MarketGood, prices: MarketPrices) : this(good.goods.name, prices.priceOf(good), good.supplyDemand())
}

/**
 * Public: who is done bidding is shared, so everyone sees who the game is waiting for (D09), and so is the initiative
 * order once Bidding closes (R27). [turnOrder] is this turn's places, first to last, each a player's id or null while
 * free (R26); empty during Bidding. [jumpedShips] names the ships that jumped this turn and [doneJumping] the players
 * whose jump slot has ended (R26, S02); [landingShip] is the ship whose player still has to choose where it ends up,
 * or null (R30, S03). [tradingQueue] is the goods still to resolve in Trading, in the order they will resolve, and
 * [resolvedGoods] the ones resolved so far (R25, R36); both empty before Trading. [resolution] is the good resolving
 * now, or the last one resolved, and null before the first. [doneInvesting] lists who has signalled done during
 * Investment (R44), so everyone sees who the game waits for (D09). [collected] lists the players with factories who have
 * confirmed where their output goes (R47, E06-S03-5), public in the same way. Offers go here only once Trading has
 * started, when every offer of the turn becomes public as [publicOffers] (R17). Bids are secret while Bidding is open
 * (R29); once it closes, every bid and roll is public as [initiativeRolls], in initiative order (creator, 2026-11-18).
 */
data class TurnResponseDto(
	val number: Int,
	val phase: String,
	val doneBidding: List<String>,
	val initiative: List<String>,
	val turnOrder: List<String?>,
	val jumpedShips: List<String>,
	val doneJumping: List<String>,
	val landingShip: String?,
	val tradingQueue: List<MarketGoodResponseDto>,
	val resolvedGoods: List<MarketGoodResponseDto>,
	val resolution: ResolutionResponseDto?,
	val doneInvesting: List<String>,
	val collected: List<String>,
	val interception: InterceptionResponseDto?,
	val battle: BattleResponseDto?,
	val publicOffers: List<PublicOfferResponseDto>,
	val initiativeRolls: List<InitiativeRollResponseDto>,
	val doneOpportunity: List<String>,
	val opportunist: String?,
	val owedChoices: List<String>,
	val limpyAsk: String?,
	val doneNews: List<String>
) {

	/** [ships] are every ship in the game, for a battle's ships' details (R78). */
	constructor(turn: Turn, ids: PlayerIds, ships: List<Ship>) : this(
		turn.number,
		turn.phase.name,
		ids.ofAll(turn.doneBidding),
		ids.ofAll(turn.initiative),
		turn.turnOrder().map { email -> email?.let { ids.of(it) } },
		turn.jumpedShips,
		ids.ofAll(turn.doneJumping),
		turn.landingShip,
		turn.tradingQueue.map { MarketGoodResponseDto(it) },
		turn.resolvedGoods.map { MarketGoodResponseDto(it) },
		turn.resolution?.let { ResolutionResponseDto(it, ids) },
		ids.ofAll(turn.doneInvesting),
		ids.ofAll(turn.collected),
		turn.interception?.let { InterceptionResponseDto(it, ids) },
		turn.battle?.let { BattleResponseDto(it, ids, ships) },
		turn.publicOffers().map { PublicOfferResponseDto(it, ids) },
		turn.initiativeRolls.map { InitiativeRollResponseDto(it, ids) },
		ids.ofAll(turn.doneOpportunity),
		turn.opportunist()?.let { ids.of(it) },
		ids.ofAll(turn.owedChoices()),
		turn.limpyAsk?.let { ids.of(it) },
		ids.ofAll(turn.doneNews)
	)
}

/**
 * What one player bid for initiative and rolled (R27), public once Bidding closes (creator, 2026-11-18): [total] is bid +
 * [roll] + [bonus], [bonusFrom] the agent who gave the bonus (Wolf, shown and so revealed: R105, playtest 2026-11-20), and
 * [tieRolls] any rolls made to break a tie, in order.
 */
data class InitiativeRollResponseDto(
	val playerId: String,
	val bid: Int,
	val roll: Int,
	val bonus: Int,
	val bonusFrom: String?,
	val total: Int,
	val tieRolls: List<Int>
) {

	constructor(roll: InitiativeRoll, ids: PlayerIds) : this(
		ids.of(roll.playerEmail),
		roll.bid,
		roll.rolls.first(),
		roll.bonus,
		roll.bonusFrom(),
		roll.total(),
		roll.tieRolls()
	)
}

/**
 * The good resolving now, or the last one resolved (E05-S02). Public: [offers] are disclosed to everyone once the good
 * starts resolving (S02-1), the Dictator's last if they placed one. [dictator] is the id of the good's Dictator at
 * that system, or null; [awaitingDictator] is that id while they still decide (R38, D09), else null; [finished] says
 * whether anything is left to decide. [startingCounter] is the good's S/D plus the roll, null until the counter starts
 * once nobody is asked (E05-S03-1); [trades] are the offers the counter reached, in order (S03-2 to S03-5, E05-S04).
 * [awaitingTrade] is the offer whose player the game waits for to choose an amount, with the counter's max, else null
 * (E05-S04-1, D09). [startPrice] is the good's price when it started resolving and [newPrice] its price once trading is
 * over, else null (E05-S05-1, S05-2). [wholesalers] are who the wholesale step asks, in order, [awaitingWholesaler] the
 * one it waits for, else null, and [wholesales] their answers so far (R37, S05-4, S05-5).
 */
data class ResolutionResponseDto(
	val good: MarketGoodResponseDto,
	val offers: List<DisclosedOfferResponseDto>,
	val dictator: String?,
	val awaitingDictator: String?,
	val finished: Boolean,
	val startingCounter: Int?,
	val trades: List<TradeResponseDto>,
	val awaitingTrade: AwaitingTradeResponseDto?,
	val startPrice: Int,
	val newPrice: Int?,
	val wholesalers: List<String>,
	val awaitingWholesaler: String?,
	val wholesales: List<WholesaleResponseDto>
) {

	constructor(resolution: Resolution, ids: PlayerIds) : this(
		MarketGoodResponseDto(resolution.good),
		resolution.offers.map { DisclosedOfferResponseDto(it, ids) },
		resolution.dictator?.let { ids.of(it) },
		resolution.awaitingDictator()?.let { ids.of(it) },
		resolution.isFinished(),
		resolution.startingCounter,
		resolution.trades.map { TradeResponseDto(it, ids) },
		resolution.awaitingTrade()?.let { AwaitingTradeResponseDto(it, ids) },
		resolution.startPrice,
		resolution.newPrice(),
		ids.ofAll(resolution.wholesalers),
		resolution.awaitingWholesaler()?.let { ids.of(it) },
		resolution.wholesales.map { WholesaleResponseDto(it, resolution.wholesaleRole(it.playerEmail), ids) }
	)
}

/**
 * An answer in the wholesale step, public (E05-S05-5): whose, as Dictator or Wholesaler ([role]), Buy or Sell ([type],
 * null for a pass), the units (0 for a pass) and the new price they traded at.
 */
data class WholesaleResponseDto(val playerId: String, val role: String, val type: String?, val units: Int, val price: Int) {

	constructor(wholesale: Wholesale, role: String, ids: PlayerIds) : this(
		ids.of(wholesale.playerEmail),
		role,
		wholesale.type?.name,
		wholesale.units,
		wholesale.price
	)
}

/**
 * A trade in a resolution, public like the resolution (E05-S03, E05-S04-6): whose offer, Buy or Sell, its price, the
 * most units the market allowed and the units traded, 0 when its player traded nothing. Why is their own business (R29).
 * [positionRoll] is the market-position roll a trade of 6 or more units made, else null (R43, E05-S06-4).
 */
data class TradeResponseDto(
	val playerId: String,
	val type: String,
	val price: Int,
	val marketMax: Int,
	val units: Int,
	val positionRoll: PositionRollResponseDto?
) {

	constructor(trade: Trade, ids: PlayerIds) : this(
		ids.of(trade.offer.playerEmail),
		trade.offer.type.name,
		trade.offer.price,
		trade.marketMax,
		trade.units,
		trade.positionRoll?.let { PositionRollResponseDto(it, ids) }
	)
}

/**
 * A position roll, public (E05-S06-4): the 2d6 [roll], the tier its trader holds now ([promotedTo], null for no change),
 * the id of the Dictator it demoted to Wholesaler, if any (S06-3), and the level its trader's business connections rose
 * to, null when they didn't (R83, E12-S02-6).
 */
data class PositionRollResponseDto(val roll: Int, val promotedTo: String?, val demoted: List<String>, val businessConnections: Int?) {

	constructor(positionRoll: PositionRoll, ids: PlayerIds) : this(
		positionRoll.roll,
		positionRoll.promotedTo?.name,
		ids.ofAll(positionRoll.demoted),
		positionRoll.businessConnections
	)
}

/**
 * The offer whose player the game waits for (E05-S04-1, D09), public: whose, Buy or Sell, its price, and [maxUnits], the
 * most the counter allows. That player's own max, after money and cargo, is only in [YouResponseDto.tradeMax] (R29).
 */
data class AwaitingTradeResponseDto(val playerId: String, val type: String, val price: Int, val maxUnits: Int) {

	constructor(trade: AwaitedTrade, ids: PlayerIds) : this(
		ids.of(trade.offer.playerEmail),
		trade.offer.type.name,
		trade.offer.price,
		trade.marketMax
	)
}

/** An offer once its good resolves, which every player may see (S02-1): whose, Buy or Sell, and its price. */
data class DisclosedOfferResponseDto(val playerId: String, val type: String, val price: Int) {

	constructor(offer: Offer, ids: PlayerIds) : this(ids.of(offer.playerEmail), offer.type.name, offer.price)
}

/** One of this turn's offers once Trading has started and every offer is public (R17): who, where, and the terms. */
data class PublicOfferResponseDto(val playerId: String, val system: String, val goods: String, val type: String, val price: Int) {

	constructor(offer: Offer, ids: PlayerIds) : this(
		ids.of(offer.playerEmail),
		offer.system.name,
		offer.goods.name,
		offer.type.name,
		offer.price
	)
}

/** One good at one system, as Trading resolves it: [system] and [goods] are the constants, [systemName] for display. */
data class MarketGoodResponseDto(val system: String, val systemName: String, val goods: String) {

	constructor(good: MarketGood) : this(good.system.name, good.system.displayName, good.goods.name)
}

/** One public event of the Game log (s04) and the turn it happened in, which the screen groups by (S06-1). */
data class LogEntryResponseDto(
	val turn: Int,
	val text: String,
	val trade: TradeRecordResponseDto?,
	val notice: NoticeResponseDto?,
	val battleSummary: List<BattleSummarySectionResponseDto>?
) {

	constructor(entry: LogEntry, ids: PlayerIds) : this(
		entry.turn,
		entry.text,
		entry.trade?.let { TradeRecordResponseDto(it, ids) },
		entry.notice?.let { NoticeResponseDto(it, ids) },
		entry.battleSummary?.sections?.map { BattleSummarySectionResponseDto(it) }
	)
}

/** One part of a finished battle's summary (playtest, 2026-11-20): "Round 2" and its log lines. Public, like the log. */
data class BattleSummarySectionResponseDto(val title: String, val lines: List<String>) {

	constructor(section: BattleSummarySection) : this(section.title, section.lines)
}

/** The interception or battle moment a log entry tells of, for the pop-up cards (creator, 2026-11-18); public, like the line. */
data class NoticeResponseDto(
	val kind: String,
	val system: String,
	val systemName: String,
	val playerIds: List<String>,
	val round: Int?,
	val interception: InterceptionRollResponseDto?
) {

	constructor(notice: Notice, ids: PlayerIds) : this(
		notice.kind.name,
		notice.system.name,
		notice.system.displayName,
		ids.ofAll(notice.players),
		notice.round,
		notice.interception?.let { InterceptionRollResponseDto(it, ids) }
	)
}

/** One interception try with both rolls as the log words them, and whether it held the ship (R70, R71). */
data class InterceptionRollResponseDto(
	val interceptorId: String,
	val interceptorShip: String,
	val interceptorRoll: String,
	val arrivingId: String,
	val arrivingShip: String,
	val arrivingRoll: String,
	val success: Boolean
) {

	constructor(roll: InterceptionRoll, ids: PlayerIds) : this(
		ids.of(roll.interceptorEmail),
		roll.interceptorShip,
		roll.interceptorRoll,
		ids.of(roll.arrivingEmail),
		roll.arrivingShip,
		roll.arrivingRoll,
		roll.success
	)
}

/** The trade a log entry tells of, for a player's trade history (creator, 2026-11-18); public, like the line itself (R18). */
data class TradeRecordResponseDto(
	val playerId: String,
	val system: String,
	val systemName: String,
	val goods: String,
	val type: String,
	val units: Int,
	val price: Int
) {

	constructor(record: TradeRecord, ids: PlayerIds) : this(
		ids.of(record.playerEmail),
		record.system.name,
		record.system.displayName,
		record.goods.name,
		record.type.name,
		record.units,
		record.price
	)
}

/**
 * Only for the viewer: their [id], which names them in everything public ([PlayerIds]), their money (R29), what their own ships and warehouses carry (S02-5, R49), their own offers this turn (R17), their
 * own initiative bid this turn, or null if they haven't placed one (R29), and [tradeMax], the most they may trade now
 * when the game waits for their amount (E05-S04-1), after their money and cargo (R12, R10), 0 when that leaves nothing
 * (S04-5), else null. [wholesaleMax] is the same for the wholesale step when it waits for them (R37, E05-S05-4), else
 * null. [warehouses] are their own warehouses with what each holds (R29, like their ships' cargo), and [collect] is what
 * they place at the start of Investment while they still have to (R47, E06-S03-1), else null: not in Investment,
 * already confirmed, or no factories (S03-6). [invest] is what they may buy while they can (E06-S04), else null.
 * [assets] is where their money sits, for the Assets tab (creator, 2026-11-18).
 */
data class YouResponseDto(
	val id: String,
	val money: Int,
	val ships: List<YourShipResponseDto>,
	val offers: List<YourOfferResponseDto>,
	val untradedOffers: List<YourOfferResponseDto>,
	val bid: Int?,
	val tradeMax: Int?,
	val wholesaleMax: WholesaleMaxResponseDto?,
	val warehouses: List<YourWarehouseResponseDto>,
	val collect: CollectResponseDto?,
	val invest: InvestResponseDto?,
	val interception: YourInterceptionResponseDto?,
	val battle: YourBattleResponseDto?,
	val battleReport: BattleReportResponseDto?,
	val assets: AssetsResponseDto,
	val loan: YourLoanResponseDto?,
	val agents: List<YourAgentResponseDto>,
	val agentPool: Int,
	val sabotagesOrdered: List<YourSabotageResponseDto>,
	val sabotagesSuffered: List<SufferedSabotageResponseDto>,
	val sabotagedWarehouse: YourWarehouseResponseDto?,
	val agentOffers: List<AgentOfferResponseDto>,
	val limpyAsk: MarketGoodResponseDto?,
	val opportunity: YourOpportunityResponseDto?,
	val factorySales: List<FactorySaleResponseDto>,
	val ledger: List<MoneyMovementResponseDto>,
	val tipOffs: List<TipOffResponseDto>
) {

	/** Maps [view], which [Game.viewFor] already limited to what its player may see (R17, R29); [ids] name the players. */
	constructor(view: PlayerView, ids: PlayerIds) : this(view, ids.of(view.player.email), view.player, ids)

	private constructor(view: PlayerView, id: String, player: Player, ids: PlayerIds) : this(
		id,
		player.money,
		player.ships.map { YourShipResponseDto(it, view.landingOdds.getValue(it.name)) },
		view.offers.map { YourOfferResponseDto(it) },
		view.untradedOffers.map { YourOfferResponseDto(it) },
		view.bid?.amount,
		view.tradeMax,
		view.wholesaleAsk?.let { WholesaleMaxResponseDto(player, it) },
		player.warehouses.map { YourWarehouseResponseDto(it) },
		view.factoriesToCollect?.let { CollectResponseDto(player, it) },
		view.investment?.let { InvestResponseDto(player, view.prices, it.freeShipNames, it.raisedConnection, it.boughtReputation) },
		view.interception?.let { YourInterceptionResponseDto(it, player) },
		view.battle?.let { YourBattleResponseDto(it, player.email, id) },
		view.battleReport?.let { BattleReportResponseDto(it, ids) },
		AssetsResponseDto(player, view.prices),
		view.loan?.let { YourLoanResponseDto(it) },
		player.agents.map { YourAgentResponseDto(it, checkNotNull(player.placeOfAgent(it.agent))) },
		view.agentPool,
		view.sabotagesOrdered.map { YourSabotageResponseDto(it, ids) },
		view.sabotagesSuffered.map { SufferedSabotageResponseDto(it) },
		view.sabotagedWarehouse?.let { YourWarehouseResponseDto(it) },
		view.agentOffers.map { AgentOfferResponseDto(it, ids) },
		view.limpyAsk?.let { MarketGoodResponseDto(it) },
		view.opportunity?.let { YourOpportunityResponseDto(it) },
		player.factories.map { FactorySaleResponseDto(it, view.prices) },
		player.ledger.newestFirst().map { MoneyMovementResponseDto(it) },
		view.tipOffs.map { TipOffResponseDto(it) }
	)
}

/**
 * A tip-off in play as the viewer may see it (R109–R111): its [slot], [need] ("CRIM 5") and [effectiveTurn] for everyone;
 * [kind] (OPPORTUNITY or EVENT) and [details] only once learned; [taken] once its opportunity is taken and they may know;
 * [needRefusal] why their connections can't learn it, or null.
 */
data class TipOffResponseDto(
	val slot: Int,
	val need: String,
	val effectiveTurn: Int,
	val learned: Boolean,
	val kind: String?,
	val details: String?,
	val taken: Boolean,
	val needRefusal: String?
) {

	constructor(view: TipOffView) : this(
		view.tipOff.slot,
		view.tipOff.tipOff.need.text(),
		view.tipOff.effectiveIn(),
		view.learned,
		view.tipOff.tipOff.kindName().takeIf { view.learned },
		view.tipOff.tipOff.details().takeIf { view.learned },
		view.takenShown,
		view.needRefusal
	)
}

/** One movement of the viewer's own money (R29, playtest 2026-11-20), newest first in [YouResponseDto.ledger]: green when [credit]. */
data class MoneyMovementResponseDto(val turn: Int, val reason: String, val amount: Int, val credit: Boolean) {

	constructor(movement: MoneyMovement) : this(movement.turn, movement.reason, movement.amount, movement.isCredit())
}

/** What one of the viewer's own warehouses holds (R29): [cargoUsed] counts POLY and ISO only (R42), like a ship's. */
data class YourWarehouseResponseDto(
	val system: String,
	val systemName: String,
	val blocks: Int,
	val cargoSpaces: Int,
	val cargo: Map<String, Int>,
	val cargoUsed: Int
) {

	constructor(warehouse: Warehouse) : this(
		warehouse.system.name,
		warehouse.system.displayName,
		warehouse.blocks,
		warehouse.cargoSpaces(),
		warehouse.cargo.filterValues { it > 0 }.entries.sortedBy { it.key.name }.associate { it.key.name to it.value },
		warehouse.cargoSpacesUsed()
	)
}

/**
 * What the viewer may buy in Investment once they have collected (R45, R46, E06-S04, E06-S05); no ship is needed for any of
 * it. [blockCost] HT a warehouse block, the [systems] where they can buy blocks (all six, in map order) with how many blocks
 * they own there, and the [factories] they can add units to: each good each system can produce, with the units they make
 * now, and what the [shipyard] sells (E09-S02). [factoryUnits] is the units of factory they own in total and [factoryCap]
 * the most they may own, their political + business connections (R84, E12-S03-8). [connections] is what raising each
 * connection costs them now, and [raisedConnection] whether they already raised one this Investment (R83, E12-S02).
 * [reputationCost] is what buying 1d6 of reputation costs and [boughtReputation] whether they already did this Investment
 * (R87, E13-S03).
 * Private to the viewer, and null outside Investment and while they still have to collect.
 */
data class InvestResponseDto(
	val blockCost: Int,
	val systems: List<InvestSystemResponseDto>,
	val factories: List<InvestFactoryResponseDto>,
	val shipyard: ShipyardResponseDto,
	val factoryUnits: Int,
	val factoryCap: Int,
	val connections: List<ConnectionRaiseResponseDto>,
	val raisedConnection: Boolean,
	val reputationCost: Int,
	val boughtReputation: Boolean
) {

	constructor(
		player: Player,
		prices: MarketPrices,
		freeShipNames: List<String>,
		raisedConnection: Boolean,
		boughtReputation: Boolean
	) : this(
		Warehouse.BLOCK_COST,
		StarSystem.entries.map { InvestSystemResponseDto(it, player.warehouseAt(it)?.blocks ?: 0) },
		StarSystem.entries.flatMap { system ->
			system.producibleGoods.sortedBy { it.name }.map { InvestFactoryResponseDto(player, system, it, prices) }
		},
		ShipyardResponseDto(freeShipNames),
		player.factoryUnits(),
		player.factoryCap(),
		ConnectionKind.entries.map { ConnectionRaiseResponseDto(it, player.connections.of(it), player.connectionRaiseCost(it)) },
		raisedConnection,
		Reputation.BUY_COST,
		boughtReputation
	)
}

/** A connection the viewer may raise (R83): its `ConnectionKind` constant as [kind], its [level] now, and the [cost] of the next point, null at 10. */
data class ConnectionRaiseResponseDto(val kind: String, val name: String, val level: Int, val cost: Int?) {

	constructor(kind: ConnectionKind, level: Int, cost: Int?) : this(kind.name, kind.displayName, level, cost)
}

/**
 * What a shipyard sells (R60, R61, R65): the [shipyards] (in map order), the civilian [hulls] and [modules] with their
 * prices (R56: no military ones), what a crew member of each class costs, and [freeNames], the suggested names nobody in
 * the game uses yet (R62).
 */
data class ShipyardResponseDto(
	val shipyards: List<ShipyardSystemResponseDto>,
	val hulls: List<ShipyardHullResponseDto>,
	val modules: List<ShipyardModuleResponseDto>,
	val crewClasses: List<CrewClassResponseDto>,
	val freeNames: List<String>
) {

	constructor(freeNames: List<String>) : this(
		StarSystem.shipyards().map { ShipyardSystemResponseDto(it.name, it.displayName) },
		Hull.forSale().map { ShipyardHullResponseDto(it) },
		Module.forSale().map { ShipyardModuleResponseDto(it) },
		CrewClass.entries.map { CrewClassResponseDto(it) },
		freeNames
	)
}

data class ShipyardSystemResponseDto(val system: String, val systemName: String)

/**
 * A hull for sale (R51): its `Hull` constant as [id], what the buy form needs to price and fit it, and the rest of its
 * characteristics (base [weapons], [shields], [intercept] and [repairCost]) so a buyer can compare hulls.
 */
data class ShipyardHullResponseDto(
	val id: String,
	val name: String,
	val price: Int,
	val moduleCapacity: Int,
	val crew: Int,
	val cargoSpaces: Int,
	val weapons: Int,
	val shields: Int,
	val intercept: Int,
	val repairCost: Int,
	val aerodynamic: Boolean
) {

	constructor(hull: Hull) : this(
		hull.name,
		hull.displayName,
		hull.price,
		hull.moduleCapacity,
		hull.crew,
		hull.builtInCargo,
		hull.weapons,
		hull.shields,
		hull.intercept,
		hull.repairCost,
		hull.aerodynamic
	)
}

/** A module for sale (R52): its `Module` constant as [id]; Safe jump takes no module capacity and a ship has one at most. */
data class ShipyardModuleResponseDto(
	val id: String,
	val name: String,
	val price: Int,
	val crew: Int,
	val cargoSpaces: Int,
	val weapons: Int,
	val passengers: Int,
	val takesModuleCapacity: Boolean
) {

	constructor(module: Module) : this(
		module.name,
		module.displayName,
		module.price,
		module.crew,
		module.cargoSpaces,
		module.weapons,
		module.passengers,
		module.takesModuleCapacity
	)
}

/** A crew class to choose (R67) with what it adds to jumps (R68) and interception (R71), shown beside it (playtest, 2026-11-20). */
data class CrewClassResponseDto(val id: String, val pricePerCrew: Int, val jumpModifier: Int, val interceptModifier: Int) {

	constructor(crewClass: CrewClass) : this(crewClass.name, crewClass.pricePerCrew, crewClass.jumpModifier, crewClass.interceptModifier)
}

/**
 * A ship to buy (R61, E09-S02): [name], the [shipyard] system, the [hull] and [modules] as `Hull` and `Module` constants
 * (a module once per unit), and the [crewClass] (`A` to `D`). Names are checked here.
 */
data class BuyShipRequestDto(
	val name: String? = null,
	val shipyard: String? = null,
	val hull: String? = null,
	val modules: List<String>? = null,
	val crewClass: String? = null
) {

	fun shipyardSystem(): StarSystem? = StarSystem.entries.firstOrNull { it.name == shipyard }

	fun hullConstant(): Hull? = Hull.entries.firstOrNull { it.name == hull }

	/** The modules, or null when one of them isn't a module; none when missing. */
	fun moduleConstants(): List<Module>? = modules.orEmpty().map { name -> Module.entries.firstOrNull { it.name == name } ?: return null }

	fun crewClassConstant(): CrewClass? = CrewClass.entries.firstOrNull { it.name == crewClass }
}

/** A module to fit to a ship (R65): its `Module` constant. */
data class FitModuleRequestDto(val module: String? = null) {

	fun moduleConstant(): Module? = moduleNamed(module)
}

/** R89: the crew class a ship's new crew is of, `A` to `D`. */
data class ReplaceCrewRequestDto(val crewClass: String? = null) {

	fun crewClassConstant(): CrewClass? = CrewClass.entries.firstOrNull { it.name == crewClass }
}

/** The module whose `Module` constant is [name], or null for none: for a request body or a path alike. */
fun moduleNamed(name: String?): Module? = Module.entries.firstOrNull { it.name == name }

/** A system, and the [blocks] of warehouse the viewer already owns there (0 for none). */
data class InvestSystemResponseDto(val system: String, val systemName: String, val blocks: Int) {

	constructor(system: StarSystem, blocks: Int) : this(system.name, system.displayName, blocks)
}

/**
 * A good the viewer can add factory units for at [system] (R46): the [size] they make a turn now (0 for no factory yet),
 * the good's market [price] there, and what one unit of size costs, [unitCost] = 5 × the price + 10 HT.
 */
data class InvestFactoryResponseDto(
	val system: String,
	val systemName: String,
	val goods: String,
	val size: Int,
	val price: Int,
	val unitCost: Int
) {

	constructor(player: Player, system: StarSystem, goods: Goods, prices: MarketPrices) :
		this(player, system, goods, prices.priceOf(MarketGood(system, goods)))

	private constructor(player: Player, system: StarSystem, goods: Goods, price: Int) : this(
		system.name,
		system.displayName,
		goods.name,
		player.factoryAt(system, goods)?.size ?: 0,
		price,
		ProductionCost(price).perUnit()
	)
}

/** The blocks to buy and where (R45, E06-S04). Names are checked here; [blocks] is a [BigDecimal] so 2.5 reaches a message instead of being rounded. */
data class BuyBlocksRequestDto(val system: String? = null, val blocks: BigDecimal? = null) {

	/** The system, or null when it's missing or isn't one. */
	fun starSystem(): StarSystem? = StarSystem.entries.firstOrNull { it.name == system }

	/** The blocks as a whole number, or null when missing or not whole. */
	fun wholeBlocks(): Int? = blocks?.let { runCatching { it.intValueExact() }.getOrNull() }
}

/** The units of production to buy and where (R46, E06-S05). Names are checked here; [units] is a [BigDecimal] so 2.5 reaches a message instead of being rounded. */
data class BuyUnitsRequestDto(val system: String? = null, val goods: String? = null, val units: BigDecimal? = null) {

	/** The system, or null when it's missing or isn't one. */
	fun starSystem(): StarSystem? = StarSystem.entries.firstOrNull { it.name == system }

	/** The good, or null when it's missing or isn't one. */
	fun goodsConstant(): Goods? = Goods.entries.firstOrNull { it.name == goods }

	/** The units as a whole number, or null when missing or not whole. */
	fun wholeUnits(): Int? = units?.let { runCatching { it.intValueExact() }.getOrNull() }
}

/**
 * A move of goods (R58, E08-S02, revised 2026-11-17): [units] of [goods] [from] one of the caller's holders [to] another
 * in the same port. Each end is a ship's name, or left out for the caller's warehouse there. [units] is a [BigDecimal]
 * so 2.5 reaches a message instead of being rounded.
 */
data class MoveCargoRequestDto(val from: String? = null, val to: String? = null, val goods: String? = null, val units: BigDecimal? = null) {

	/** The good, or null when it's missing or isn't one. */
	fun goodsConstant(): Goods? = Goods.entries.firstOrNull { it.name == goods }

	/** The units as a whole number, or null when missing or not whole. */
	fun wholeUnits(): Int? = units?.let { runCatching { it.intValueExact() }.getOrNull() }

	/** Where the goods come from: the ship [from], or the warehouse when it's left out. */
	fun source(): CargoHolder = from?.let { CargoHolder.Ship(it) } ?: CargoHolder.Warehouse

	/** Where the goods go: the ship [to], or the warehouse when it's left out. */
	fun destination(): CargoHolder = to?.let { CargoHolder.Ship(it) } ?: CargoHolder.Warehouse
}

/**
 * What the viewer's factories made this turn and where it can go (R47, E06-S03-1, S03-2): one entry per factory, with a
 * destination for their warehouse there and each of their ships in port there. Private to the viewer, and only while they
 * still have to confirm.
 */
data class CollectResponseDto(val factories: List<CollectFactoryResponseDto>) {

	constructor(player: Player, factories: List<Factory>) : this(factories.map { CollectFactoryResponseDto(player, it) })
}

/** A factory's output of [units] of [goods] made at [system] (R46, R47), and the [destinations] it can go to there. */
data class CollectFactoryResponseDto(
	val system: String,
	val systemName: String,
	val goods: String,
	val units: Int,
	val destinations: List<CollectDestinationResponseDto>
) {

	constructor(player: Player, factory: Factory) : this(
		factory.system.name,
		factory.system.displayName,
		factory.goods.name,
		factory.size,
		listOfNotNull(player.warehouseAt(factory.system)?.let { CollectDestinationResponseDto(it, factory.goods) }) +
			player.shipsInPortAt(factory.system).map { CollectDestinationResponseDto(it, factory.goods) }
	)
}

/**
 * Where output can go: the viewer's ship called [ship], or their warehouse when [ship] is null. [freeSpaces] is the free
 * cargo space for POLY and ISO, null for CHIPS and FOOD, which take none (R42, E06-S03-2).
 */
data class CollectDestinationResponseDto(val ship: String?, val freeSpaces: Int?) {

	constructor(warehouse: Warehouse, goods: Goods) : this(null, warehouse.freeSpacesFor(goods))

	constructor(ship: Ship, goods: Goods) : this(ship.name, ship.freeSpacesFor(goods))
}

/**
 * The most the viewer may [buy] and [sell] in the wholesale step at the new price (R37): at most 5 u, a buy capped by
 * their money (R12), a sell by what their ships in port there and their warehouse there hold (R10, R49); 0 when they can't.
 * Private (R29).
 */
data class WholesaleMaxResponseDto(val buy: Int, val sell: Int) {

	constructor(player: Player, resolution: Resolution) : this(
		player.tradeMax(resolution.wholesaleOffer(OfferType.BUY), Wholesale.MAX_UNITS),
		player.tradeMax(resolution.wholesaleOffer(OfferType.SELL), Wholesale.MAX_UNITS)
	)
}

/** One of the viewer's own offers; there is no player field, since it can only be theirs. */
data class YourOfferResponseDto(val system: String, val systemName: String, val goods: String, val type: String, val price: Int) {

	constructor(offer: Offer) : this(offer.system.name, offer.system.displayName, offer.goods.name, offer.type.name, offer.price)
}

/**
 * An offer to place. Names are checked here, so an unknown one is a `400` with a message rather than Jackson's; [price]
 * is a [BigDecimal] so 2.5 reaches the domain's "whole number" message instead of being rounded to 2.
 */
data class PlaceOfferRequestDto(
	val system: String? = null,
	val goods: String? = null,
	val type: String? = null,
	val price: BigDecimal? = null
) {

	fun system(): StarSystem? = StarSystem.entries.firstOrNull { it.name == system }

	fun goods(): Goods? = Goods.entries.firstOrNull { it.name == goods }

	fun type(): OfferType? = OfferType.entries.firstOrNull { it.name == type }

	/** The price as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholePrice(): Int? = price?.let { runCatching { it.intValueExact() }.getOrNull() }
}

// BigDecimal, not Int, for the same reason as the turns and the price: 2.5 must reach the domain's message, not become 2.
data class PlaceBidRequestDto(val amount: BigDecimal? = null) {

	/** The amount as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholeAmount(): Int? = amount?.let { runCatching { it.intValueExact() }.getOrNull() }
}

// BigDecimal, not Int, for the same reason as the bid: 2.5 reaches the domain's message instead of being rounded.
data class PickPlaceRequestDto(val place: BigDecimal? = null) {

	/** The place as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholePlace(): Int? = place?.let { runCatching { it.intValueExact() }.getOrNull() }
}

/** A ship jump. The destination's name is checked here, so an unknown one reaches the domain's message as a missing one. */
data class JumpShipRequestDto(val ship: String? = null, val destination: String? = null) {

	fun destination(): StarSystem? = StarSystem.entries.firstOrNull { it.name == destination }
}

/** Where the ship that just jumped ends up; an unknown location reaches the domain's message as a missing one. */
data class LandShipRequestDto(val location: String? = null) {

	fun location(): ShipLocation? = ShipLocation.entries.firstOrNull { it.name == location }
}

/**
 * One of the viewer's own ships, for them only (R29, R57): [cargo] is units per good (only goods it carries),
 * [cargoUsed] counts POLY and ISO only (R42) and [cargoSpaces] comes from the hull and modules (R54). [hull], [modules]
 * (one entry per different module, with how many) and [stats] are what the Fleet tab shows (E07-S03); nobody else's
 * are ever sent (S03-6). [jumpChances] is its chance to jump to each other system, keyed by `StarSystem` constant (R68).
 */
data class YourShipResponseDto(
	val name: String,
	val cargo: Map<String, Int>,
	val cargoUsed: Int,
	val cargoSpaces: Int,
	val hull: HullResponseDto,
	val modules: List<FittedModuleResponseDto>,
	val stats: ShipStatsResponseDto,
	val crewClass: String,
	val saleValue: Int,
	val canRefit: Boolean,
	val canRepair: Boolean,
	val leavesYardOnTurn: Int?,
	val jumpChances: Map<String, JumpChanceResponseDto>,
	val hullDamage: Int,
	val repairCost: Int,
	val landing: LandingOddsResponseDto,
	val specialLots: List<SpecialLotResponseDto>,
	val deliveryJob: String?,
	val contraband: Boolean
) {

	/** [odds] are its chances on its system's Planet this turn (R118). */
	constructor(ship: Ship, odds: LandingOdds) : this(
		ship.name,
		ship.cargo.filterValues { it > 0 }.entries.sortedBy { it.key.name }.associate { it.key.name to it.value },
		ship.cargoSpacesUsed(),
		ship.cargoSpaces(),
		HullResponseDto(ship.hull),
		ship.fittedModules().map { (module, count) -> FittedModuleResponseDto(module, count, ship.damagedCount(module)) },
		ShipStatsResponseDto(ship),
		ship.crewClass.name,
		ship.saleValue(),
		ship.canBeRefitted(),
		ship.canBeRepaired(),
		ship.leavesYardOnTurn(),
		ship.jumpChances().entries.associate { (system, chance) -> system.name to JumpChanceResponseDto(chance) },
		ship.hullDamage,
		ship.repairCost(),
		LandingOddsResponseDto(odds),
		ship.specialLots.map { SpecialLotResponseDto(it) },
		ship.deliveryJob?.opportunity?.description(),
		ship.isContraband()
	)
}

/** A ship's chances on its system's Planet this turn (R118), as whole percentages, with the [security] and its [bonus]. */
data class LandingOddsResponseDto(val lands: Int, val forced: Int, val seized: Int, val security: Int, val bonus: Int) {

	constructor(odds: LandingOdds) : this(odds.landsPercent(), odds.forcedPercent(), odds.seizedPercent(), odds.security, odds.bonus)
}

/** Goods bought through an opportunity aboard one of the viewer's ships (R112): what, how many, and where they sell. */
data class SpecialLotResponseDto(
	val goods: String,
	val name: String,
	val units: Int,
	val sellAt: String,
	val sellAtName: String,
	val illegal: Boolean
) {

	constructor(lot: SpecialLot) : this(
		lot.goods.name,
		lot.goods.displayName,
		lot.units,
		lot.sellAt.name,
		lot.sellAt.displayName,
		lot.isIllegal()
	)
}

/** A ship's chance to jump to one system (R68): a 2d6 roll of [target] or less succeeds, [percent] of the time. */
data class JumpChanceResponseDto(val target: Int, val percent: Int) {

	constructor(chance: JumpChance) : this(chance.target, chance.percent())
}

/** A ship's hull by name, with its flags (R51, R56); its numbers are part of the ship's [ShipStatsResponseDto]. */
data class HullResponseDto(val name: String, val military: Boolean, val aerodynamic: Boolean) {

	constructor(hull: Hull) : this(hull.displayName, hull.military, hull.aerodynamic)
}

/**
 * A module a ship carries [count] of, [damaged] of them damaged (R76), with what one of them adds (R52): the figures are
 * per module, not times [count].
 */
data class FittedModuleResponseDto(
	val id: String,
	val name: String,
	val count: Int,
	val damaged: Int,
	val weapons: Int,
	val rocketWeapons: Int,
	val crew: Int,
	val cargoSpaces: Int,
	val passengers: Int,
	val military: Boolean,
	val resaleValue: Int
) {

	constructor(module: Module, count: Int, damaged: Int) : this(
		module.name,
		module.displayName,
		count,
		damaged,
		module.weapons,
		module.rocketWeapons,
		module.crew,
		module.cargoSpaces,
		module.passengers,
		module.military,
		module.resaleValue()
	)
}

/** A ship's derived stats (R53); its cargo spaces are on [YourShipResponseDto], next to what it carries. */
data class ShipStatsResponseDto(
	val weapons: Int,
	val rocketWeapons: Int,
	val shields: Int,
	val intercept: Int,
	val crewNeeded: Int,
	val passengers: Int,
	val modulesUsed: Int,
	val moduleCapacity: Int
) {

	constructor(ship: Ship) : this(
		ship.weapons(),
		ship.rocketWeapons(),
		ship.shields(),
		ship.intercept(),
		ship.crewNeeded(),
		ship.passengers(),
		ship.modulesUsed(),
		ship.moduleCapacity()
	)
}

/**
 * The goods still to resolve, in their new order (R25, S01-3). Names are checked here, so an unknown one is a `400` with
 * a message rather than Jackson's.
 */
data class ReorderQueueRequestDto(val queue: List<MarketGoodRequestDto>? = null) {

	/** The order as goods, or null when it's missing or names something that isn't a good at a system. */
	fun order(): List<MarketGood>? = queue?.map { it.marketGood() ?: return null }
}

data class MarketGoodRequestDto(val system: String? = null, val goods: String? = null) {

	fun marketGood(): MarketGood? {
		val system = StarSystem.entries.firstOrNull { it.name == system } ?: return null
		val goods = Goods.entries.firstOrNull { it.name == goods } ?: return null
		return MarketGood(system, goods)
	}
}

/**
 * Where the viewer's factories' output goes (R47, E06-S03-2). Names are checked here, so an unknown one is a `400` with a
 * message rather than Jackson's; numbers are [BigDecimal] so 2.5 reaches a message instead of being rounded to 2.
 */
data class CollectRequestDto(val placements: List<OutputPlacementRequestDto>? = null) {

	/** The placements, empty when absent (everything lost), or null when one has an unknown name or isn't a whole number. */
	fun outputPlacements(): List<OutputPlacement>? = placements.orEmpty().map { it.outputPlacement() ?: return null }
}

/** One placement: [units] of the factory for [goods] at [system] go into [ship], or into the warehouse there when it's null. */
data class OutputPlacementRequestDto(
	val system: String? = null,
	val goods: String? = null,
	val ship: String? = null,
	val units: BigDecimal? = null
) {

	fun outputPlacement(): OutputPlacement? {
		val system = StarSystem.entries.firstOrNull { it.name == system } ?: return null
		val goods = Goods.entries.firstOrNull { it.name == goods } ?: return null
		val whole = units?.let { runCatching { it.intValueExact() }.getOrNull() } ?: return null
		return OutputPlacement(system, goods, ship, whole)
	}
}

/**
 * The awaited trader's amount and how it splits across their ships in port there and their warehouse there ([warehouse]
 * units, absent for none) (E05-S04-1, S04-3, R49). Numbers are [BigDecimal] so 2.5 reaches the domain's "whole number"
 * message instead of being rounded to 2.
 */
data class TradeRequestDto(val amount: BigDecimal? = null, val ships: List<ShipLoadRequestDto>? = null, val warehouse: BigDecimal? = null) {

	/** The warehouse's units as a whole number, 0 when absent, or null when they aren't whole. */
	fun warehouseUnits(): Int? {
		val units = warehouse ?: return 0
		return runCatching { units.intValueExact() }.getOrNull()
	}

	/** The amount as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholeAmount(): Int? = amount?.let { runCatching { it.intValueExact() }.getOrNull() }

	/** The split, empty when absent (no ship in port there, or only a warehouse), or null when a ship's units aren't a whole number or its name is missing. */
	fun loads(): List<ShipLoad>? = ships.orEmpty().map { it.shipLoad() ?: return null }
}

data class ShipLoadRequestDto(val name: String? = null, val units: BigDecimal? = null) {

	fun shipLoad(): ShipLoad? {
		val whole = units?.let { runCatching { it.intValueExact() }.getOrNull() } ?: return null
		return name?.let { ShipLoad(it, whole) }
	}
}

/**
 * A wholesale (R37, E05-S05-4): Buy or Sell, the amount and how it splits across the player's ships in port there and their
 * warehouse there ([warehouse], R49). The
 * type's name is checked here; numbers are [BigDecimal] so 2.5 reaches the domain's "whole number" message.
 */
data class WholesaleRequestDto(
	val type: String? = null,
	val amount: BigDecimal? = null,
	val ships: List<ShipLoadRequestDto>? = null,
	val warehouse: BigDecimal? = null
) {

	fun type(): OfferType? = OfferType.entries.firstOrNull { it.name == type }

	/** The amount and split, read as a trade's are. */
	fun trade(): TradeRequestDto = TradeRequestDto(amount, ships, warehouse)
}

/**
 * The Dictator's offer (R38). The type's name is checked here, so an unknown one is a `400` with a message; [price] is a
 * [BigDecimal] so 2.5 reaches the domain's "whole number" message instead of being rounded to 2.
 */
data class DictatorOfferRequestDto(val type: String? = null, val price: BigDecimal? = null) {

	fun type(): OfferType? = OfferType.entries.firstOrNull { it.name == type }

	/** The price as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholePrice(): Int? = price?.let { runCatching { it.intValueExact() }.getOrNull() }
}
