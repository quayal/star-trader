package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Battle
import dev.adriangrzebyk.trader.game.BattleAction
import dev.adriangrzebyk.trader.game.BattleOrder
import dev.adriangrzebyk.trader.game.BattleStage
import dev.adriangrzebyk.trader.game.HitPlacement
import dev.adriangrzebyk.trader.game.Interception
import dev.adriangrzebyk.trader.game.InvalidBattleException
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.ModuleHit
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Ship
import dev.adriangrzebyk.trader.game.ShipLocation
import dev.adriangrzebyk.trader.trading.Goods
import java.math.BigDecimal

/**
 * The arrival other players decide whether to intercept (R70), public: who is [asked] and who the game still [awaiting]s.
 * Their answers stay secret until the tries are rolled and logged.
 */
data class InterceptionResponseDto(
	val system: String,
	val systemName: String,
	val ship: String,
	val ownerId: String,
	val asked: List<String>,
	val awaiting: List<String>
) {

	constructor(interception: Interception, ids: PlayerIds) : this(
		interception.system.name,
		interception.system.displayName,
		interception.ship,
		ids.of(interception.ownerEmail),
		ids.ofAll(interception.asked),
		ids.ofAll(interception.awaiting())
	)
}

/**
 * A battle under way (R73–R78), public: its round and [stage], the ships still fighting and those that broke away, each
 * with its hull, modules, damage and crew class (R78, an exception to R57), whom it [waitingFor], and the hits each ship
 * took this round while their owners place them. Orders never go here (R74).
 */
data class BattleResponseDto(
	val system: String,
	val systemName: String,
	val round: Int,
	val stage: String,
	val ships: List<BattleShipResponseDto>,
	val leftShips: List<BattleShipResponseDto>,
	val waitingFor: List<String>,
	val pendingHits: List<PendingHitsResponseDto>
) {

	/** [ships] are every ship in the game, to look the battle's up by name. */
	constructor(battle: Battle, ids: PlayerIds, ships: List<Ship>) : this(
		battle.system.name,
		battle.system.displayName,
		battle.round,
		battle.stage.name,
		battle.ships.mapNotNull { fighter ->
			ships.firstOrNull {
				it.name == fighter.name
			}?.let { BattleShipResponseDto(it, ids.of(fighter.ownerEmail), battle.interceptorsOf(it.name)) }
		},
		battle.leftShips.mapNotNull { left ->
			ships.firstOrNull { it.name == left.name }?.let { BattleShipResponseDto(it, ids.of(left.ownerEmail), emptyList()) }
		},
		ids.ofAll(battle.waitingFor()),
		battle.pendingHits.map { PendingHitsResponseDto(it.ship, ids.of(it.ownerEmail), it.hits) }
	)
}

/** [hits] [ship] took this round, waiting for its owner, [ownerId], to place them (R76). */
data class PendingHitsResponseDto(val ship: String, val ownerId: String, val hits: Int)

/**
 * A ship in a battle, shown to every player while it lasts (R78): its hull, [hullDamage] against its shields, its modules
 * with how many are damaged, its stats from working modules (R76), its crew class, and [interceptedBy]: the ships still
 * fighting that caught it, while any is it can't break away (R74).
 */
data class BattleShipResponseDto(
	val name: String,
	val ownerId: String,
	val hull: HullResponseDto,
	val hullDamage: Int,
	val modules: List<FittedModuleResponseDto>,
	val stats: ShipStatsResponseDto,
	val cargoSpaces: Int,
	val crewClass: String,
	val interceptedBy: List<String>
) {

	constructor(ship: Ship, ownerId: String, interceptedBy: List<String>) : this(
		ship.name,
		ownerId,
		HullResponseDto(ship.hull),
		ship.hullDamage,
		ship.fittedModules().map { (module, count) -> FittedModuleResponseDto(module, count, ship.damagedCount(module)) },
		ShipStatsResponseDto(ship),
		ship.cargoSpaces(),
		ship.crewClass.name,
		interceptedBy
	)
}

/**
 * What the viewer has to do about the interception, when they're asked (R70): the [ships] of theirs that can try (one of
 * them may), and [answered] with the one they chose ([chosen], null for none) once they have. Null for everyone else.
 */
data class YourInterceptionResponseDto(val ships: List<String>, val answered: Boolean, val chosen: String?) {

	constructor(interception: Interception, player: Player) : this(
		player.shipsInSpaceAt(interception.system).map { it.name },
		interception.answerOf(player.email) != null,
		interception.answerOf(player.email)?.ship
	)
}

/**
 * The viewer's part in the battle (R74–R77): their ships still [fighting], those of them that [mayBreakAway] (nothing
 * that caught them still fights), the [orders] they gave this round (secret to them until it resolves), the [hitsToPlace]
 * on their ships, and the ships they place when it's over ([toLand], empty once they have).
 */
data class YourBattleResponseDto(
	val fighting: List<String>,
	val mayBreakAway: List<String>,
	val orders: List<BattleOrderResponseDto>,
	val hitsToPlace: List<PendingHitsResponseDto>,
	val toLand: List<String>
) {

	constructor(battle: Battle, viewerEmail: String, viewerId: String) : this(
		battle.fightingShipsOf(viewerEmail),
		battle.fightingShipsOf(viewerEmail).filter { battle.canBreakAway(it) },
		battle.ordersOf(viewerEmail).map { BattleOrderResponseDto(it) },
		battle.pendingHits.filter { it.ownerEmail == viewerEmail }.map { PendingHitsResponseDto(it.ship, viewerId, it.hits) },
		battle.survivorsOf(viewerEmail).takeIf { battle.stage == BattleStage.LANDING && viewerEmail !in battle.done }.orEmpty()
	)
}

/** One of the viewer's own orders this round (R74). */
data class BattleOrderResponseDto(val ship: String, val action: String, val target: String?, val rockets: Boolean) {

	constructor(order: BattleOrder) : this(order.ship, order.action.name, order.target, order.rockets)
}

/** Which one of the player's ships in Space tries to intercept (R70): null or absent is "none". */
data class InterceptionRequestDto(val ship: String? = null)

/** This round's orders for each of the player's ships in the battle (R74). */
data class BattleOrdersRequestDto(val orders: List<BattleOrderRequestDto>? = null) {

	/** The orders, refused with a message for an unknown action or a missing ship. */
	fun battleOrders(): List<BattleOrder> = orders.orEmpty().map { it.battleOrder() }
}

data class BattleOrderRequestDto(
	val ship: String? = null,
	val action: String? = null,
	val target: String? = null,
	val rockets: Boolean? = null
) {

	fun battleOrder(): BattleOrder {
		val action = BattleAction.entries.firstOrNull { it.name == action }
			?: throw InvalidBattleException("Order each ship to FIRE or BREAK_AWAY.")
		val ship = ship ?: throw InvalidBattleException("Name the ship each order is for.")
		return BattleOrder(ship, action, target.takeIf { action == BattleAction.FIRE }, action == BattleAction.FIRE && rockets == true)
	}
}

/** Where the player puts the hits each of their ships took this round (R76). */
data class BattleHitsRequestDto(val ships: List<ShipHitsRequestDto>? = null) {

	/** The placements by ship, refused with a message for a ship named twice or a part that isn't a whole number. */
	fun placements(): Map<String, HitPlacement> {
		val all = ships.orEmpty().map { (it.ship ?: throw InvalidBattleException("Name each ship you place hits on.")) to it.placement() }
		if (all.map { it.first }.distinct().size != all.size) {
			throw InvalidBattleException("Place each ship's hits once.")
		}
		return all.toMap()
	}
}

data class ShipHitsRequestDto(
	val ship: String? = null,
	val hull: BigDecimal? = null,
	val modules: List<ModuleHitRequestDto>? = null,
	val jettison: Map<String, BigDecimal>? = null
) {

	fun placement(): HitPlacement = HitPlacement(
		whole(hull ?: BigDecimal.ZERO),
		modules.orEmpty().map { it.moduleHit() },
		jettison.orEmpty().entries.associate { (goods, units) ->
			(Goods.entries.firstOrNull { it.name == goods } ?: throw InvalidBattleException("Only POLY and ISO can be thrown overboard.")) to
				whole(units)
		}
	)
}

data class ModuleHitRequestDto(val module: String? = null, val damaged: Boolean? = null, val hits: BigDecimal? = null) {

	fun moduleHit(): ModuleHit {
		val fitted = Module.entries.firstOrNull { it.name == module } ?: throw InvalidBattleException("Choose a fitted module to hit.")
		return ModuleHit(fitted, damaged == true, whole(hits ?: BigDecimal.ZERO))
	}
}

/** Where the player puts each of their surviving ships from the battle (R77). */
data class BattleLandingRequestDto(val ships: List<ShipLandingRequestDto>? = null) {

	/** The locations by ship, refused with a message for an unknown location or a ship named twice. */
	fun locations(): Map<String, ShipLocation> {
		val all = ships.orEmpty().map { landing ->
			val ship = landing.ship ?: throw InvalidBattleException("Name each ship you place.")
			ship to (ShipLocation.entries.firstOrNull { it.name == landing.location } ?: throw InvalidBattleException("Choose where $ship ends up."))
		}
		if (all.map { it.first }.distinct().size != all.size) {
			throw InvalidBattleException("Place each ship once.")
		}
		return all.toMap()
	}
}

data class ShipLandingRequestDto(val ship: String? = null, val location: String? = null)

/** [value] as a whole number of hits or units, refused otherwise. */
private fun whole(value: BigDecimal): Int =
	runCatching { value.intValueExact() }.getOrNull() ?: throw InvalidBattleException("Hits and units are whole numbers.")
