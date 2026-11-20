package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Connections
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Reputation
import dev.adriangrzebyk.trader.game.ledger.MoneyLedger
import dev.adriangrzebyk.trader.game.news.Expedition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Player] as the attribute map nested in the game item; each ship is a [ShipItem], each market position a [MarketPositionItem], each warehouse a [WarehouseItem], each factory a [FactoryItem]. */
internal class PlayerItem(val attributes: Map<String, AttributeValue>) {

	constructor(player: Player) : this(
		mapOf(
			EMAIL to AttributeValue.fromS(player.email),
			NAME to AttributeValue.fromS(player.name),
			CORPORATION_NAME to AttributeValue.fromS(player.corporationName),
			MONEY to number(player.money),
			SHIPS to AttributeValue.fromL(player.ships.map { AttributeValue.fromM(ShipItem(it).attributes) }),
			MARKET_POSITIONS to AttributeValue.fromL(player.marketPositions.map { AttributeValue.fromM(MarketPositionItem(it).attributes) }),
			WAREHOUSES to AttributeValue.fromL(player.warehouses.map { AttributeValue.fromM(WarehouseItem(it).attributes) }),
			FACTORIES to AttributeValue.fromL(player.factories.map { AttributeValue.fromM(FactoryItem(it).attributes) }),
			POLITICAL to number(player.connections.political),
			BUSINESS to number(player.connections.business),
			CRIMINAL to number(player.connections.criminal),
			CONNECTION_POINTS to number(player.connectionPoints),
			REPUTATION to number(player.reputation.value),
			AGENTS to AttributeValue.fromL(player.agents.map { AttributeValue.fromM(RecruitedAgentItem(it).attributes) }),
			LEDGER_TURN to number(player.ledger.turn),
			LEDGER to AttributeValue.fromL(player.ledger.movements.map { AttributeValue.fromM(MoneyMovementItem(it).attributes) }),
			EXPEDITIONS to AttributeValue.fromL(
				player.expeditions.map {
					AttributeValue.fromM(mapOf(SHIP to AttributeValue.fromM(ShipItem(it.ship).attributes), RETURN_TURN to number(it.returnTurn)))
				}
			)
		) + listOfNotNull(player.loan?.let { LOAN to AttributeValue.fromM(LoanItem(it).attributes) })
	)

	fun toPlayer(): Player = Player(
		email = attributes.attribute(EMAIL).s(),
		name = attributes.attribute(NAME).s(),
		corporationName = attributes.attribute(CORPORATION_NAME).s(),
		money = attributes.attribute(MONEY).n().toInt(),
		ships = attributes.attribute(SHIPS).l().map { ShipItem(it.m()).toShip() },
		// Items saved before E03-S02 have no positions; nobody held one then (R40, BACKWARD_COMPATIBILITY.md §7).
		marketPositions = attributes[MARKET_POSITIONS]?.l()?.map { MarketPositionItem(it.m()).toMarketPosition() } ?: emptyList(),
		// Items saved before E06-S02 have no warehouses or factories; nobody had one then (R40, BACKWARD_COMPATIBILITY.md §7).
		warehouses = attributes[WAREHOUSES]?.l()?.map { WarehouseItem(it.m()).toWarehouse() } ?: emptyList(),
		factories = attributes[FACTORIES]?.l()?.map { FactoryItem(it.m()).toFactory() } ?: emptyList(),
		// Items saved before E12 have no connections: a missing level or points load as 0 (creator, 2026-11-18: saved games
		// needn't stay compatible, the game isn't published).
		connections = Connections(level(POLITICAL), level(BUSINESS), level(CRIMINAL)),
		connectionPoints = level(CONNECTION_POINTS),
		// Items saved before E13 have no reputation: they load at the start value, 20 (R85).
		reputation = Reputation(attributes[REPUTATION]?.n()?.toInt() ?: Reputation.START),
		// Absent when there's no loan, which is also how items saved before loans (R90) read.
		loan = attributes[LOAN]?.m()?.let { LoanItem(it).toLoan() },
		// Absent from items saved before agents (R103): nobody had one.
		agents = attributes[AGENTS]?.l().orEmpty().map { RecruitedAgentItem(it.m()).toRecruitedAgent() },
		// Absent from items saved before the ledger (playtest, 2026-11-20): its history starts empty, in turn 1 until the next turn.
		ledger = MoneyLedger(
			attributes[LEDGER_TURN]?.n()?.toInt() ?: MoneyLedger.START.turn,
			attributes[LEDGER]?.l().orEmpty().map { MoneyMovementItem(it.m()).toMoneyMovement() }
		),
		// Absent before the expedition (R114): none away.
		expeditions = attributes[EXPEDITIONS]?.l().orEmpty().map {
			Expedition(ShipItem(it.m().attribute(SHIP).m()).toShip(), it.m().attribute(RETURN_TURN).n().toInt())
		}
	)

	private fun level(name: String): Int = attributes[name]?.n()?.toInt() ?: 0

	private companion object {
		const val EMAIL = "email"
		const val NAME = "name"
		const val CORPORATION_NAME = "corporationName"
		const val MONEY = "money"
		const val SHIPS = "ships"
		const val MARKET_POSITIONS = "marketPositions"
		const val WAREHOUSES = "warehouses"
		const val FACTORIES = "factories"
		const val POLITICAL = "political"
		const val BUSINESS = "business"
		const val CRIMINAL = "criminal"
		const val CONNECTION_POINTS = "connectionPoints"
		const val REPUTATION = "reputation"
		const val LOAN = "loan"
		const val AGENTS = "agents"
		const val LEDGER_TURN = "ledgerTurn"
		const val LEDGER = "ledger"
		const val EXPEDITIONS = "expeditions"
		const val SHIP = "ship"
		const val RETURN_TURN = "returnTurn"
	}
}
