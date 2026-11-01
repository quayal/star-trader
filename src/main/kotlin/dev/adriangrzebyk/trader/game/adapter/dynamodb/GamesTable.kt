package dev.adriangrzebyk.trader.game.adapter.dynamodb

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType

/** Creates the games table on DynamoDB Local. In real AWS the table is provisioned outside the app (E02-S03 Q4). */
class GamesTable(private val client: DynamoDbClient, private val tableName: String) {

	fun createIfMissing() {
		try {
			client.describeTable { it.tableName(tableName) }
		} catch (_: ResourceNotFoundException) {
			client.createTable {
				it.tableName(tableName)
					.keySchema(KeySchemaElement.builder().attributeName(GameItem.ID).keyType(KeyType.HASH).build())
					.attributeDefinitions(
						AttributeDefinition.builder().attributeName(GameItem.ID).attributeType(ScalarAttributeType.S).build()
					)
					.billingMode(BillingMode.PAY_PER_REQUEST)
			}
			client.waiter().waitUntilTableExists { it.tableName(tableName) }
		}
	}
}
