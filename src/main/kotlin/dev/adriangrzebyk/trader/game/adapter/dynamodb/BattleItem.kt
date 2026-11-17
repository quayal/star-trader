package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleAction
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import dev.adriangrzebyk.trader.game.battle.BattleOrder
import dev.adriangrzebyk.trader.game.battle.BattleShip
import dev.adriangrzebyk.trader.game.battle.BattleStage
import dev.adriangrzebyk.trader.game.battle.PendingHits
import dev.adriangrzebyk.trader.trading.StarSystem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Battle] as the attribute map nested in its turn's item (engineering standards §12). */
internal class BattleItem(val attributes: Map<String, AttributeValue>) {

	constructor(battle: Battle) : this(
		mapOf(
			SYSTEM to AttributeValue.fromS(battle.system.name),
			ROUND to number(battle.round),
			STAGE to AttributeValue.fromS(battle.stage.name),
			SHIPS to AttributeValue.fromL(battle.ships.map { AttributeValue.fromM(BattleShipItem(it).attributes) }),
			INTERCEPTIONS to AttributeValue.fromL(
				battle.interceptions.map {
					AttributeValue.fromM(mapOf(SHIP to AttributeValue.fromS(it.ship), INTERCEPTOR to AttributeValue.fromS(it.interceptor)))
				}
			),
			LEFT_SHIPS to AttributeValue.fromL(battle.leftShips.map { AttributeValue.fromM(BattleShipItem(it).attributes) }),
			ORDERS to AttributeValue.fromL(
				battle.orders.map { order ->
					AttributeValue.fromM(
						mapOf(
							SHIP to AttributeValue.fromS(order.ship),
							ACTION to AttributeValue.fromS(order.action.name),
							ROCKETS to AttributeValue.fromBool(order.rockets)
						) + listOfNotNull(order.target?.let { TARGET to AttributeValue.fromS(it) })
					)
				}
			),
			PENDING_HITS to AttributeValue.fromL(
				battle.pendingHits.map {
					AttributeValue.fromM(
						mapOf(SHIP to AttributeValue.fromS(it.ship), OWNER_EMAIL to AttributeValue.fromS(it.ownerEmail), HITS to number(it.hits))
					)
				}
			),
			DONE to strings(battle.done)
		)
	)

	fun toBattle(): Battle = Battle(
		system = StarSystem.valueOf(attributes.attribute(SYSTEM).s()),
		round = attributes.attribute(ROUND).n().toInt(),
		stage = BattleStage.valueOf(attributes.attribute(STAGE).s()),
		ships = attributes.attribute(SHIPS).l().map { BattleShipItem(it.m()).toBattleShip() },
		interceptions = attributes.attribute(INTERCEPTIONS).l().map { item ->
			BattleInterception(ship = item.m().attribute(SHIP).s(), interceptor = item.m().attribute(INTERCEPTOR).s())
		},
		leftShips = attributes.attribute(LEFT_SHIPS).l().map { BattleShipItem(it.m()).toBattleShip() },
		orders = attributes.attribute(ORDERS).l().map { item ->
			val fields = item.m()
			BattleOrder(
				ship = fields.attribute(SHIP).s(),
				action = BattleAction.valueOf(fields.attribute(ACTION).s()),
				target = fields[TARGET]?.s(),
				rockets = fields.attribute(ROCKETS).bool()
			)
		},
		pendingHits = attributes.attribute(PENDING_HITS).l().map { item ->
			val fields = item.m()
			PendingHits(ship = fields.attribute(SHIP).s(), ownerEmail = fields.attribute(OWNER_EMAIL).s(), hits = fields.attribute(HITS).n().toInt())
		},
		done = attributes.attribute(DONE).l().map { it.s() }
	)

	private companion object {
		const val SYSTEM = "system"
		const val ROUND = "round"
		const val STAGE = "stage"
		const val SHIPS = "ships"
		const val INTERCEPTIONS = "interceptions"
		const val INTERCEPTOR = "interceptor"
		const val LEFT_SHIPS = "leftShips"
		const val ORDERS = "orders"
		const val PENDING_HITS = "pendingHits"
		const val DONE = "done"
		const val SHIP = "ship"
		const val OWNER_EMAIL = "ownerEmail"
		const val ACTION = "action"
		const val TARGET = "target"
		const val ROCKETS = "rockets"
		const val HITS = "hits"
	}
}

/** A [BattleShip] as the attribute map nested in its battle's item. */
internal class BattleShipItem(val attributes: Map<String, AttributeValue>) {

	constructor(ship: BattleShip) : this(mapOf(SHIP to AttributeValue.fromS(ship.name), OWNER_EMAIL to AttributeValue.fromS(ship.ownerEmail)))

	fun toBattleShip(): BattleShip = BattleShip(name = attributes.attribute(SHIP).s(), ownerEmail = attributes.attribute(OWNER_EMAIL).s())

	private companion object {
		const val SHIP = "ship"
		const val OWNER_EMAIL = "ownerEmail"
	}
}
