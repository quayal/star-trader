package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameNotFoundException
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.LogEntry
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes
import software.amazon.awssdk.services.dynamodb.model.Put
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException

/**
 * Keeps each game as one DynamoDB item plus one item per turn of its log ([LogTurnItem]), so no item grows with the
 * length of the game. Reads are consistent, so a write is visible to the next poll (A01).
 * [update] is optimistic: it writes only if the game item's version hasn't moved since it was read, and otherwise
 * reads again and retries, so two players acting at once never overwrite each other (engineering standards §9). The
 * game item and the log items a change touches are written in one transaction, so a game and its log never part.
 *
 * A read is two requests, the game item and then its log items, not one snapshot: when another change lands in between,
 * the log read may already hold that change's lines. For [update] that's harmless, since the version check then fails
 * and it reads again; a poll shows those lines one poll before the state they belong to.
 */
class DynamoDbGameStore(private val client: DynamoDbClient, private val tableName: String) : GameStore {

	override fun create(game: Game) {
		val created = write(
			game,
			version = 0,
			storedLog = emptyMap(),
			condition = "attribute_not_exists(#key)",
			names = mapOf(
				"#key" to GameItem.ID
			)
		)
		check(created) { "A game with id ${game.id} already exists" }
	}

	override fun find(id: String): Game? = read(id)?.game

	/**
	 * R119: a scan of the whole table, filtered to games in their lobby. Fine for the playtest's handful of games; an index
	 * on the status is the next step if the table grows. A lobby has no log yet, but its log items are read like any game's.
	 */
	override fun lobbies(): List<Game> = client.scanPaginator {
		it.tableName(tableName)
			.filterExpression("#status = :lobby AND attribute_exists(#creator)")
			.expressionAttributeNames(mapOf("#status" to GameItem.STATUS, "#creator" to GameItem.CREATOR_EMAIL))
			.expressionAttributeValues(mapOf(":lobby" to AttributeValue.fromS(GameStatus.LOBBY.name)))
			.consistentRead(true)
	}.items().map { GameItem(it) }.map { item -> item.toGame(readLog(item.id, item.logTurns()).values.flatten()) }

	override fun update(id: String, mutate: (Game) -> Game): Game {
		repeat(MAX_ATTEMPTS) {
			val stored = read(id) ?: throw GameNotFoundException()
			val updated = mutate(stored.game)
			val written = write(
				updated,
				version = stored.version + 1,
				storedLog = stored.logItems,
				condition = "#version = :expected",
				names = mapOf("#version" to GameItem.VERSION),
				values = mapOf(":expected" to AttributeValue.fromN(stored.version.toString()))
			)
			if (written) {
				return updated
			}
			// Another change landed after our read; read the new version and apply mutate again.
		}
		throw IllegalStateException("Gave up updating game $id after $MAX_ATTEMPTS attempts: it kept changing underneath")
	}

	/** A game as read: its [version], and [logItems], each turn's entries as its log item holds them (none for a log still inside the game item). */
	private class StoredGame(val game: Game, val version: Long, val logItems: Map<Int, List<LogEntry>>)

	// The table also holds other kinds of item (`session#…`, `log#…`); a game id is a UUID and has no `#`.
	private fun read(id: String): StoredGame? {
		if ('#' in id) return null
		val item = client.getItem {
			it.tableName(tableName).key(mapOf(GameItem.ID to AttributeValue.fromS(id))).consistentRead(true)
		}.takeIf { it.hasItem() }?.let { GameItem(it.item()) } ?: return null
		val logItems = readLog(id, item.logTurns())
		return StoredGame(item.toGame(logItems.values.flatten()), item.version, logItems)
	}

	/** The log items of [turns] of game [id], by turn in turn order; a batch answers in any order and may need asking again for what it left out. */
	private fun readLog(id: String, turns: List<Int>): Map<Int, List<LogEntry>> {
		val found = turns.chunked(BATCH_GET_LIMIT).flatMap { chunk ->
			var keys = chunk.map { mapOf(LogTurnItem.ID to AttributeValue.fromS(logTurnItemId(id, it))) }
			val items = mutableListOf<LogTurnItem>()
			while (keys.isNotEmpty()) {
				val request = KeysAndAttributes.builder().keys(keys).consistentRead(true).build()
				val response = client.batchGetItem { it.requestItems(mapOf(tableName to request)) }
				items += response.responses()[tableName].orEmpty().map { LogTurnItem(it) }
				keys = response.unprocessedKeys()[tableName]?.keys().orEmpty()
			}
			items
		}
		check(found.size == turns.size) { "Game $id lists ${turns.size} turns of log but ${found.size} were found" }
		return found.sortedBy { it.turn }.associate { it.turn to it.entries() }
	}

	/**
	 * Writes [game] at [version] if [condition] holds for its item, with the log items of the turns whose entries differ
	 * from [storedLog], all or nothing. False when the condition failed, or another transaction was writing the game at
	 * the same moment: someone else got there first.
	 */
	private fun write(
		game: Game,
		version: Long,
		storedLog: Map<Int, List<LogEntry>>,
		condition: String,
		names: Map<String, String>,
		values: Map<String, AttributeValue> = emptyMap()
	): Boolean {
		val gameItem = GameItem(game, version).attributes
		val logItems = game.log.groupBy { it.turn }.filter { (turn, entries) -> storedLog[turn] != entries }
			.map { (turn, entries) -> LogTurnItem(game.id, turn, entries).attributes }
		try {
			if (logItems.isEmpty()) {
				client.putItem { put ->
					put.tableName(tableName).item(gameItem).conditionExpression(condition).expressionAttributeNames(names)
					if (values.isNotEmpty()) put.expressionAttributeValues(values)
				}
				return true
			}
			val conditionalPut = Put.builder().tableName(tableName).item(gameItem).conditionExpression(condition).expressionAttributeNames(names)
			if (values.isNotEmpty()) conditionalPut.expressionAttributeValues(values)
			val puts = listOf(conditionalPut.build()) + logItems.map { Put.builder().tableName(tableName).item(it).build() }
			client.transactWriteItems { transaction -> transaction.transactItems(puts.map { TransactWriteItem.builder().put(it).build() }) }
			return true
		} catch (_: ConditionalCheckFailedException) {
			return false
		} catch (ex: TransactionCanceledException) {
			if (ex.cancellationReasons().any { it.code() in LOST_THE_RACE }) {
				return false
			}
			throw ex
		}
	}

	companion object {
		// At most 5 players can join at once and each round lets at least one through, so 10 leaves a margin.
		private const val MAX_ATTEMPTS = 10

		/** The most keys one BatchGetItem takes; a game has at most 50 turns (R41), so one request does in practice. */
		private const val BATCH_GET_LIMIT = 100

		/** Why a transaction was cancelled when another writer got to the game first: its version moved, or both wrote at once. */
		private val LOST_THE_RACE = setOf("ConditionalCheckFailed", "TransactionConflict")
	}
}
