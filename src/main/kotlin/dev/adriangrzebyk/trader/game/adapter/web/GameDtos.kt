package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.AwaitedTrade
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.InitiativeBid
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.PositionRoll
import dev.adriangrzebyk.trader.game.Resolution
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLoad
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.Trade
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.Wholesale
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import java.math.BigDecimal

// BigDecimal, not Int: Jackson would otherwise round 2.5 down to 2 and create a game the player didn't ask for.
data class CreateGameRequestDto(val turns: BigDecimal? = null) {

	/** The length as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholeTurns(): Int? = turns?.let { runCatching { it.intValueExact() }.getOrNull() }
}

/**
 * One player's view of a game: public state for everyone, plus [you] with what only the viewer may see (R29, their
 * ships' cargo, S02-7, and their own offers, R17). [board] is the six systems with this game's prices, which Trading
 * moves (E05-S05, R35).
 */
data class GameResponseDto(
	val id: String,
	val turns: Int,
	val status: String,
	val creatorEmail: String,
	val players: List<PlayerResponseDto>,
	val turn: TurnResponseDto?,
	val log: List<LogEntryResponseDto>,
	val board: List<SystemResponseDto>,
	val you: YouResponseDto?
) {

	/** Only [viewerEmail]'s own money goes into the response; null [you] for a viewer who isn't a player. */
	constructor(game: Game, viewerEmail: String) : this(
		game.id,
		game.turns,
		game.status.name,
		game.creatorEmail,
		game.players.map { PlayerResponseDto(it) },
		game.turn?.let { TurnResponseDto(it) },
		game.log.map { LogEntryResponseDto(it) },
		StarSystem.entries.map { SystemResponseDto(it, game.prices) },
		game.players.firstOrNull { it.email == viewerEmail }?.let {
			YouResponseDto(it, game.turn?.offersOf(it.email).orEmpty(), game.turn?.bidOf(it.email), game.turn?.resolution)
		}
	)
}

/** Public: no money and no cargo here, ever (R29, S02-7). Market positions are public (R28). */
data class PlayerResponseDto(
	val email: String,
	val name: String,
	val corporationName: String,
	val ships: List<ShipResponseDto>,
	val marketPositions: List<MarketPositionResponseDto>
) {

	constructor(player: Player) : this(
		player.email,
		player.name,
		player.corporationName,
		player.ships.map { ShipResponseDto(it) },
		player.marketPositions.map { MarketPositionResponseDto(it) }
	)
}

/** Where a ship is, which every player may see; what it carries is only in [YourShipResponseDto]. */
data class ShipResponseDto(val name: String, val system: String, val systemName: String, val location: String) {

	constructor(ship: Ship) : this(ship.name, ship.system.name, ship.system.displayName, ship.location.name)
}

data class MarketPositionResponseDto(val system: String, val goods: String, val tier: String) {

	constructor(position: MarketPosition) : this(position.system.name, position.goods.name, position.tier.name)
}

/** One system of the board: its R31 location statistics and its market at this game's [MarketPrices] (D07, D08, R35). */
data class SystemResponseDto(
	val id: String,
	val name: String,
	val portClass: Int,
	val legalSystem: Int,
	val police: Int,
	val security: Int,
	val market: List<MarketEntryResponseDto>
) {

	constructor(system: StarSystem, prices: MarketPrices) : this(
		system.name,
		system.displayName,
		system.locations.portClass,
		system.locations.legalSystem,
		system.locations.police,
		system.locations.security,
		system.goods.sortedBy { it.name }.map { MarketEntryResponseDto(MarketGood(system, it), prices) }
	)
}

/** [price] is this game's (R35); [indicator] is the S/D number, which never changes; the client steps it into a market label (D08). */
data class MarketEntryResponseDto(val goods: String, val price: Int, val indicator: Int) {

	constructor(good: MarketGood, prices: MarketPrices) : this(good.goods.name, prices.priceOf(good), good.supplyDemand())
}

/**
 * Public: who is done bidding is shared, so everyone sees who the game is waiting for (D09), and so is the initiative
 * order once Bidding closes (R27). [turnOrder] is this turn's places, first to last, each an email or null while
 * free (R26); empty during Bidding. [jumpedShips] names the ships that jumped this turn and [doneJumping] the players
 * whose jump slot has ended (R26, S02); [landingShip] is the ship whose player still has to choose where it ends up,
 * or null (R30, S03). [tradingQueue] is the goods still to resolve in Trading, in the order they will resolve, and
 * [resolvedGoods] the ones resolved so far (R25, R36); both empty before Trading. [resolution] is the good resolving
 * now, or the last one resolved, and null before the first. Offers never go here except the disclosed ones of
 * [resolution] (R17, E05-S02-1), and bids never do (R29).
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
	val resolution: ResolutionResponseDto?
) {

	constructor(turn: Turn) : this(
		turn.number,
		turn.phase.name,
		turn.doneBidding,
		turn.initiative,
		turn.turnOrder(),
		turn.jumpedShips,
		turn.doneJumping,
		turn.landingShip,
		turn.tradingQueue.map { MarketGoodResponseDto(it) },
		turn.resolvedGoods.map { MarketGoodResponseDto(it) },
		turn.resolution?.let { ResolutionResponseDto(it) }
	)
}

/**
 * The good resolving now, or the last one resolved (E05-S02). Public: [offers] are disclosed to everyone once the good
 * starts resolving (S02-1), the Dictator's last if they placed one. [dictator] is the email of the good's Dictator at
 * that system, or null; [awaitingDictator] is that email while they still decide (R38, D09), else null; [finished] says
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

	constructor(resolution: Resolution) : this(
		MarketGoodResponseDto(resolution.good),
		resolution.offers.map { DisclosedOfferResponseDto(it) },
		resolution.dictator,
		resolution.awaitingDictator(),
		resolution.isFinished(),
		resolution.startingCounter,
		resolution.trades.map { TradeResponseDto(it) },
		resolution.awaitingTrade()?.let { AwaitingTradeResponseDto(it) },
		resolution.startPrice,
		resolution.newPrice(),
		resolution.wholesalers,
		resolution.awaitingWholesaler(),
		resolution.wholesales.map { WholesaleResponseDto(it, resolution.wholesaleRole(it.playerEmail)) }
	)
}

/**
 * An answer in the wholesale step, public (E05-S05-5): whose, as Dictator or Wholesaler ([role]), Buy or Sell ([type],
 * null for a pass), the units (0 for a pass) and the new price they traded at.
 */
data class WholesaleResponseDto(val playerEmail: String, val role: String, val type: String?, val units: Int, val price: Int) {

	constructor(wholesale: Wholesale, role: String) : this(wholesale.playerEmail, role, wholesale.type?.name, wholesale.units, wholesale.price)
}

/**
 * A trade in a resolution, public like the resolution (E05-S03, E05-S04-6): whose offer, Buy or Sell, its price, the
 * most units the market allowed and the units traded, 0 when its player traded nothing. Why is their own business (R29).
 * [positionRoll] is the market-position roll a trade of 6 or more units made, else null (R43, E05-S06-4).
 */
data class TradeResponseDto(
	val playerEmail: String,
	val type: String,
	val price: Int,
	val marketMax: Int,
	val units: Int,
	val positionRoll: PositionRollResponseDto?
) {

	constructor(trade: Trade) : this(
		trade.offer.playerEmail,
		trade.offer.type.name,
		trade.offer.price,
		trade.marketMax,
		trade.units,
		trade.positionRoll?.let { PositionRollResponseDto(it) }
	)
}

/**
 * A position roll, public (E05-S06-4): the 2d6 [roll], the tier its trader holds now ([promotedTo], null for no change),
 * and the email of the Dictator it demoted to Wholesaler, if any (S06-3).
 */
data class PositionRollResponseDto(val roll: Int, val promotedTo: String?, val demoted: List<String>) {

	constructor(positionRoll: PositionRoll) : this(positionRoll.roll, positionRoll.promotedTo?.name, positionRoll.demoted)
}

/**
 * The offer whose player the game waits for (E05-S04-1, D09), public: whose, Buy or Sell, its price, and [maxUnits], the
 * most the counter allows. That player's own max, after money and cargo, is only in [YouResponseDto.tradeMax] (R29).
 */
data class AwaitingTradeResponseDto(val playerEmail: String, val type: String, val price: Int, val maxUnits: Int) {

	constructor(trade: AwaitedTrade) : this(trade.offer.playerEmail, trade.offer.type.name, trade.offer.price, trade.marketMax)
}

/** An offer once its good resolves, which every player may see (S02-1): whose, Buy or Sell, and its price. */
data class DisclosedOfferResponseDto(val playerEmail: String, val type: String, val price: Int) {

	constructor(offer: Offer) : this(offer.playerEmail, offer.type.name, offer.price)
}

/** One good at one system, as Trading resolves it: [system] and [goods] are the constants, [systemName] for display. */
data class MarketGoodResponseDto(val system: String, val systemName: String, val goods: String) {

	constructor(good: MarketGood) : this(good.system.name, good.system.displayName, good.goods.name)
}

/** One public event of the Game log (s04) and the turn it happened in, which the screen groups by (S06-1). */
data class LogEntryResponseDto(val turn: Int, val text: String) {

	constructor(entry: LogEntry) : this(entry.turn, entry.text)
}

/**
 * Only for the viewer: their money (R29), what their own ships carry (S02-5), their own offers this turn (R17), their
 * own initiative bid this turn, or null if they haven't placed one (R29), and [tradeMax], the most they may trade now
 * when the game waits for their amount (E05-S04-1), after their money and cargo (R12, R10), 0 when that leaves nothing
 * (S04-5), else null. [wholesaleMax] is the same for the wholesale step when it waits for them (R37, E05-S05-4), else
 * null.
 */
data class YouResponseDto(
	val money: Int,
	val ships: List<YourShipResponseDto>,
	val offers: List<YourOfferResponseDto>,
	val bid: Int?,
	val tradeMax: Int?,
	val wholesaleMax: WholesaleMaxResponseDto?
) {

	/**
	 * [offers] and [bid] must be [player]'s own: the caller passes `turn.offersOf(player.email)` and
	 * `turn.bidOf(player.email)`, never the whole turn's. [resolution] is the turn's, whoever it waits for.
	 */
	constructor(player: Player, offers: List<Offer>, bid: InitiativeBid?, resolution: Resolution?) : this(
		player.money,
		player.ships.map { YourShipResponseDto(it) },
		offers.map { YourOfferResponseDto(it) },
		bid?.amount,
		resolution?.awaitingTrade()?.takeIf { it.offer.playerEmail == player.email }?.let { player.tradeMax(it.offer, it.marketMax) },
		resolution?.takeIf { it.awaitingWholesaler() == player.email }?.let { WholesaleMaxResponseDto(player, it) }
	)
}

/**
 * The most the viewer may [buy] and [sell] in the wholesale step at the new price (R37): at most 5 u, a buy capped by
 * their money (R12), a sell by what their ships in port there hold (R10); 0 when they can't. Private (R29).
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

/** [cargo] is units per good (only goods it carries); [cargoUsed] counts POLY and ISO only (R42). */
data class YourShipResponseDto(val name: String, val cargo: Map<String, Int>, val cargoUsed: Int, val cargoSpaces: Int) {

	constructor(ship: Ship) : this(
		ship.name,
		ship.cargo.filterValues { it > 0 }.entries.sortedBy { it.key.name }.associate { it.key.name to it.value },
		ship.cargoSpacesUsed(),
		Ship.CARGO_SPACES
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
 * The awaited trader's amount and how it splits across their ships in port there (E05-S04-1, S04-3). Numbers are
 * [BigDecimal] so 2.5 reaches the domain's "whole number" message instead of being rounded to 2.
 */
data class TradeRequestDto(val amount: BigDecimal? = null, val ships: List<ShipLoadRequestDto>? = null) {

	/** The amount as a whole number, or null when it isn't one, so the domain rejects it with its own message. */
	fun wholeAmount(): Int? = amount?.let { runCatching { it.intValueExact() }.getOrNull() }

	/** The split, empty when absent (no ship in port there), or null when a ship's units aren't a whole number or its name is missing. */
	fun loads(): List<ShipLoad>? = ships.orEmpty().map { it.shipLoad() ?: return null }
}

data class ShipLoadRequestDto(val name: String? = null, val units: BigDecimal? = null) {

	fun shipLoad(): ShipLoad? {
		val whole = units?.let { runCatching { it.intValueExact() }.getOrNull() } ?: return null
		return name?.let { ShipLoad(it, whole) }
	}
}

/**
 * A wholesale (R37, E05-S05-4): Buy or Sell, the amount and how it splits across the player's ships in port there. The
 * type's name is checked here; numbers are [BigDecimal] so 2.5 reaches the domain's "whole number" message.
 */
data class WholesaleRequestDto(val type: String? = null, val amount: BigDecimal? = null, val ships: List<ShipLoadRequestDto>? = null) {

	fun type(): OfferType? = OfferType.entries.firstOrNull { it.name == type }

	/** The amount and split, read as a trade's are. */
	fun trade(): TradeRequestDto = TradeRequestDto(amount, ships)
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
