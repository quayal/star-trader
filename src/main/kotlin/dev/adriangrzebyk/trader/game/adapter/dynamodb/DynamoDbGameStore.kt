package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStore
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException

/**
 * Keeps each game as one DynamoDB item. Reads are consistent, so a write is visible to the next poll (A01).
 * [update] is optimistic: it writes only if the item's version hasn't moved since it was read, and otherwise
 * reads again and retries, so two players acting at once never overwrite each other (engineering standards §9).
 */
class DynamoDbGameStore(private val client: DynamoDbClient, private val tableName: String) : GameStore {

	override fun create(game: Game) {
		try {
			client.putItem {
				it.tableName(tableName)
					.item(GameItemMapper.toItem(game, version = 0))
					.conditionExpression("attribute_not_exists(#id)")
					.expressionAttributeNames(mapOf("#id" to GameItemMapper.ID))
			}
		} catch (ex: ConditionalCheckFailedException) {
			throw IllegalStateException("A game with id ${game.id} already exists", ex)
		}
	}

	override fun find(id: String): Game? = read(id)?.let { GameItemMapper.toGame(it) }

	override fun update(id: String, mutate: (Game) -> Game): Game {
		repeat(MAX_ATTEMPTS) {
			val item = read(id) ?: throw NoSuchElementException("No game with id $id")
			val version = GameItemMapper.version(item)
			val updated = mutate(GameItemMapper.toGame(item))
			try {
				client.putItem {
					it.tableName(tableName)
						.item(GameItemMapper.toItem(updated, version + 1))
						.conditionExpression("#version = :expected")
						.expressionAttributeNames(mapOf("#version" to GameItemMapper.VERSION))
						.expressionAttributeValues(mapOf(":expected" to AttributeValue.fromN(version.toString())))
				}
				return updated
			} catch (_: ConditionalCheckFailedException) {
				// Another change landed after our read; read the new version and apply mutate again.
			}
		}
		throw IllegalStateException("Gave up updating game $id after $MAX_ATTEMPTS attempts: it kept changing underneath")
	}

	private fun read(id: String): Map<String, AttributeValue>? = client.getItem {
		it.tableName(tableName).key(mapOf(GameItemMapper.ID to AttributeValue.fromS(id))).consistentRead(true)
	}.takeIf { it.hasItem() }?.item()

	companion object {
		// At most 5 players can join at once and each round lets at least one through, so 10 leaves a margin.
		private const val MAX_ATTEMPTS = 10
	}
}
