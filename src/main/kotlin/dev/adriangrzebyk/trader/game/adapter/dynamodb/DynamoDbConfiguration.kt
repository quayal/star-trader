package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.GameStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI
import java.time.Duration

/**
 * A blank endpoint override means real AWS, with the SDK's standard credential chain and a table provisioned
 * outside the app. Set, it means DynamoDB Local (what bootRun uses): dummy credentials, and the table is
 * created on startup if it's missing.
 */
@Configuration
class DynamoDbConfiguration(
	@Value("\${star-trader.dynamodb.endpoint-override}") private val endpointOverride: String,
	@Value("\${star-trader.dynamodb.table-name}") private val tableName: String,
	@Value("\${star-trader.dynamodb.region}") private val region: String
) {

	@Bean
	fun dynamoDbClient(): DynamoDbClient {
		// Without a timeout an unanswered request blocks forever, so a broken database hangs startup silently.
		val builder = DynamoDbClient.builder()
			.region(Region.of(region))
			.overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
		if (endpointOverride.isBlank()) {
			return builder.credentialsProvider(DefaultCredentialsProvider.builder().build()).build()
		}
		return builder
			.endpointOverride(URI(endpointOverride))
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
			.build()
	}

	@Bean
	fun gameStore(dynamoDbClient: DynamoDbClient): GameStore {
		if (endpointOverride.isNotBlank()) {
			try {
				GamesTable(dynamoDbClient, tableName).createIfMissing()
			} catch (ex: SdkClientException) {
				throw IllegalStateException(
					"DynamoDB Local isn't reachable at $endpointOverride. Is Docker running? bootRun starts it from compose.yaml.",
					ex
				)
			}
		}
		return DynamoDbGameStore(dynamoDbClient, tableName)
	}

	companion object {
		private val API_CALL_TIMEOUT = Duration.ofSeconds(10)
	}
}
