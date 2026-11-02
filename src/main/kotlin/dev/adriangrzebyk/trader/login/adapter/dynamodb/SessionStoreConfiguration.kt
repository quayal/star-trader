package dev.adriangrzebyk.trader.login.adapter.dynamodb

import dev.adriangrzebyk.trader.login.SessionStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

/**
 * Sessions share the games table and its client (`game/adapter/dynamodb/DynamoDbConfiguration`), which also creates
 * the table on DynamoDB Local; the table's only key is `id`, which session items use too.
 */
@Configuration
class SessionStoreConfiguration(@Value("\${star-trader.dynamodb.table-name}") private val tableName: String) {

	@Bean
	fun sessionStore(dynamoDbClient: DynamoDbClient): SessionStore = DynamoDbSessionStore(dynamoDbClient, tableName)
}
