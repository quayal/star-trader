package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.LogEntry
import dev.adriangrzebyk.trader.game.MarketPrices
import dev.adriangrzebyk.trader.game.SupplyDemandShifts
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.news.News
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * A [Game] as its DynamoDB item: one item per game, nested objects as maps and lists, each written by its own
 * class ([PlayerItem], [TurnItem]), plus the [version] the store's optimistic update checks. The game's log isn't in
 * it: each turn's entries are a [LogTurnItem] of their own, so no item grows with the length of the game, and this one
 * only lists which turns have one ([logTurns]).
 * Every field is mapped by name, so a field added to the domain doesn't compile here until it's read back;
 * [GameItemTest]'s round trip then catches a field that isn't written (engineering standards §12).
 */
internal class GameItem(val attributes: Map<String, AttributeValue>) {

	constructor(game: Game, version: Long) : this(
		mapOf(
			ID to AttributeValue.fromS(game.id),
			TURNS to number(game.turns),
			STATUS to AttributeValue.fromS(game.status.name),
			CREATOR_EMAIL to AttributeValue.fromS(game.creatorEmail),
			PLAYERS to AttributeValue.fromL(game.players.map { AttributeValue.fromM(PlayerItem(it).attributes) }),
			JOIN_REQUESTS to AttributeValue.fromL(game.joinRequests.map { AttributeValue.fromM(PlayerItem(it).attributes) }),
			// Explicit NULL rather than a missing attribute: "not started" is a value, not an old item.
			TURN to (game.turn?.let { AttributeValue.fromM(TurnItem(it).attributes) } ?: AttributeValue.fromNul(true)),
			PRICES to AttributeValue.fromL(
				game.prices.prices.map { (good, price) ->
					AttributeValue.fromM(MarketPriceItem(good, price).attributes)
				}
			),
			LOG_TURNS to AttributeValue.fromL(game.log.map { it.turn }.distinct().map { number(it) }),
			DEAD_AGENTS to strings(game.deadAgents.map { it.name }),
			NEWS to AttributeValue.fromM(NewsItem(game.news).attributes),
			SUPPLY_DEMAND_SHIFTS to
				AttributeValue.fromM(game.supplyDemandShifts.shifts.entries.associate { (goods, shift) -> goods.name to number(shift) }),
			VERSION to number(version)
		)
	)

	val version: Long
		get() = attributes.attribute(VERSION).n().toLong()

	val id: String
		get() = attributes.attribute(ID).s()

	/** The turns of this game's log that are stored as a [LogTurnItem]; none for a game saved with its log inside the item. */
	fun logTurns(): List<Int> = attributes[LOG_TURNS]?.l().orEmpty().map { it.n().toInt() }

	/**
	 * The game, with [storedLog], the entries of its [LogTurnItem]s in turn order. A game saved before the log moved out
	 * still has it inside the item (`log`) and no log items; its next save moves it (BACKWARD_COMPATIBILITY.md §7).
	 */
	fun toGame(storedLog: List<LogEntry>): Game = Game(
		id = attributes.attribute(ID).s(),
		turns = attributes.attribute(TURNS).n().toInt(),
		status = GameStatus.valueOf(attributes.attribute(STATUS).s()),
		creatorEmail = attributes.attribute(CREATOR_EMAIL).s(),
		players = attributes.attribute(PLAYERS).l().map { PlayerItem(it.m()).toPlayer() },
		// Absent from items saved before join requests (R120): nobody was asking.
		joinRequests = attributes[JOIN_REQUESTS]?.l().orEmpty().map { PlayerItem(it.m()).toPlayer() },
		turn = attributes.attribute(TURN).takeUnless { it.nul() == true }?.let { TurnItem(it.m()).toTurn() },
		// Absent on games saved before E05-S05, when prices never changed: they're the board's.
		prices = MarketPrices(
			MarketPrices.board().prices + attributes[PRICES]?.l().orEmpty().map { MarketPriceItem(it.m()) }.associate { it.good() to it.price() }
		),
		log = attributes[INLINE_LOG]?.l().orEmpty().map { LogEntryItem(it).toLogEntry() } + storedLog,
		// Absent from items saved before agents (R107): none had died.
		deadAgents = attributes[DEAD_AGENTS]?.l().orEmpty().map { Agent.valueOf(it.s()) },
		// Absent from items saved before News (R109): such a game plays on without tip-offs.
		news = attributes[NEWS]?.m()?.let { NewsItem(it).toNews() } ?: News.NONE,
		// Absent before S/D shifts (R115): none.
		supplyDemandShifts = SupplyDemandShifts(
			attributes[SUPPLY_DEMAND_SHIFTS]?.m().orEmpty().entries.associate { (goods, shift) -> Goods.valueOf(goods) to shift.n().toInt() }
		)
	)

	companion object {
		const val ID = "id"
		const val VERSION = "version"
		private const val TURNS = "turns"
		const val STATUS = "status"
		const val CREATOR_EMAIL = "creatorEmail"
		private const val PLAYERS = "players"
		private const val JOIN_REQUESTS = "joinRequests"
		private const val TURN = "turn"
		private const val PRICES = "prices"
		private const val LOG_TURNS = "logTurns"
		private const val DEAD_AGENTS = "deadAgents"
		private const val NEWS = "news"
		private const val SUPPLY_DEMAND_SHIFTS = "supplyDemandShifts"

		/** Where the whole log was kept before it moved to [LogTurnItem]s; only read, never written. */
		private const val INLINE_LOG = "log"
	}
}
