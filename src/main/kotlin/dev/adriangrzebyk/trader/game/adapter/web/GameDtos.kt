package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.InitiativeBid
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.game.Turn
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
 * ships' cargo, S02-7, and their own offers, R17). [board] is the six systems; it's fixed today and sent per game
 * so that trading can move its prices (E05) without changing the shape.
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
		StarSystem.entries.map { SystemResponseDto(it) },
		game.players.firstOrNull { it.email == viewerEmail }?.let {
			YouResponseDto(it, game.turn?.offersOf(it.email).orEmpty(), game.turn?.bidOf(it.email))
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

/** One system of the board: its R31 location statistics and its market (D07, D08). */
data class SystemResponseDto(
	val id: String,
	val name: String,
	val portClass: Int,
	val legalSystem: Int,
	val police: Int,
	val security: Int,
	val market: List<MarketEntryResponseDto>
) {

	constructor(system: StarSystem) : this(
		system.name,
		system.displayName,
		system.locations.portClass,
		system.locations.legalSystem,
		system.locations.police,
		system.locations.security,
		system.goods.sortedBy { it.name }.map { MarketEntryResponseDto(system, it) }
	)
}

/** [indicator] is the S/D number; the client steps it into a market label (D08). */
data class MarketEntryResponseDto(val goods: String, val price: Int, val indicator: Int) {

	constructor(system: StarSystem, goods: Goods) : this(goods.name, system.getPrice(goods), system.getIndicator(goods))
}

/**
 * Public: who is done bidding is shared, so everyone sees who the game is waiting for (D09), and so is the initiative
 * order once Bidding closes (R27). [turnOrder] is this turn's places, first to last, each an email or null while
 * free (R26); empty during Bidding. [jumpedShips] names the ships that jumped this turn and [doneJumping] the players
 * whose jump slot has ended (R26, S02); [landingShip] is the ship whose player still has to choose where it ends up,
 * or null (R30, S03). Offers and bids never go here (R17, R29).
 */
data class TurnResponseDto(
	val number: Int,
	val phase: String,
	val doneBidding: List<String>,
	val initiative: List<String>,
	val turnOrder: List<String?>,
	val jumpedShips: List<String>,
	val doneJumping: List<String>,
	val landingShip: String?
) {

	constructor(turn: Turn) : this(
		turn.number,
		turn.phase.name,
		turn.doneBidding,
		turn.initiative,
		turn.turnOrder(),
		turn.jumpedShips,
		turn.doneJumping,
		turn.landingShip
	)
}

/** One public event of the Game log (s04) and the turn it happened in, which the screen groups by (S06-1). */
data class LogEntryResponseDto(val turn: Int, val text: String) {

	constructor(entry: LogEntry) : this(entry.turn, entry.text)
}

/**
 * Only for the viewer: their money (R29), what their own ships carry (S02-5), their own offers this turn (R17) and
 * their own initiative bid this turn, or null if they haven't placed one (R29).
 */
data class YouResponseDto(val money: Int, val ships: List<YourShipResponseDto>, val offers: List<YourOfferResponseDto>, val bid: Int?) {

	/**
	 * [offers] and [bid] must be [player]'s own: the caller passes `turn.offersOf(player.email)` and
	 * `turn.bidOf(player.email)`, never the whole turn's.
	 */
	constructor(player: Player, offers: List<Offer>, bid: InitiativeBid?) : this(
		player.money,
		player.ships.map { YourShipResponseDto(it) },
		offers.map { YourOfferResponseDto(it) },
		bid?.amount
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
