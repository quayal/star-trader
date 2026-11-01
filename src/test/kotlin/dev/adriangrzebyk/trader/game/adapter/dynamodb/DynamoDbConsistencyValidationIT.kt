package dev.adriangrzebyk.trader.game.adapter.dynamodb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.net.URI
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * E02-S03-T01's proof of A01: with ConsistentRead=true, a read from a second client sees the write
 * before it, also with several writer/reader pairs running at once. Temporary — E02-S03's adapter
 * tests supersede it. Skipped without Docker, except on CI, where missing Docker fails it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DynamoDbConsistencyValidationIT {

	private lateinit var container: GenericContainer<*>
	private lateinit var writer: DynamoDbClient
	private lateinit var reader: DynamoDbClient

	@BeforeAll
	fun startDynamoDbLocal() {
		assumeTrue(
			System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable,
			"Skipping the DynamoDB Local consistency check: Docker is not available."
		)
		container = GenericContainer("amazon/dynamodb-local:3.3.2")
			.withExposedPorts(DYNAMODB_PORT)
			.waitingFor(Wait.forListeningPort())
		container.start()
		writer = client()
		reader = client()
		writer.createTable {
			it.tableName(TABLE)
				.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
				.attributeDefinitions(AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
				.billingMode(BillingMode.PAY_PER_REQUEST)
		}
	}

	// Each guard on its own, so a failed setup reports its own error rather than an uninitialized property.
	@AfterAll
	fun stopDynamoDbLocal() {
		if (::writer.isInitialized) writer.close()
		if (::reader.isInitialized) reader.close()
		if (::container.isInitialized) container.stop()
	}

	@Test
	fun shouldReadAGameBackFromAnotherClientRightAfterWritingIt() {
		writer.putItem { it.tableName(TABLE).item(gameItem("game-1", version = 0)) }

		assertThat(consistentVersion("game-1")).isEqualTo(0)
	}

	@Test
	fun shouldSeeEveryWriteUnderConcurrentWriterReaderPairs() {
		val writers = Executors.newFixedThreadPool(PAIRS)
		val readers = Executors.newFixedThreadPool(PAIRS)

		val slowest = try {
			val pairs = (1..PAIRS).map { pair ->
				writers.submit<Duration> {
					val gameId = "pair-$pair"
					var slowest = Duration.ZERO
					for (version in 0 until WRITES_PER_PAIR) {
						writer.putItem { it.tableName(TABLE).item(gameItem(gameId, version.toLong())) }
						val written = System.nanoTime()
						val seen = readers.submit<Long> { consistentVersion(gameId) }.get(5, TimeUnit.SECONDS)
						assertThat(seen).describedAs("version read back for %s", gameId).isEqualTo(version.toLong())
						slowest = maxOf(slowest, Duration.ofNanos(System.nanoTime() - written))
					}
					slowest
				}
			}
			pairs.maxOf { it.get(60, TimeUnit.SECONDS) }
		} finally {
			writers.shutdownNow()
			readers.shutdownNow()
		}

		assertThat(slowest).isLessThan(VISIBILITY_TARGET)
	}

	private fun consistentVersion(gameId: String): Long = reader.getItem {
		it.tableName(TABLE).key(mapOf("id" to AttributeValue.fromS(gameId))).consistentRead(true)
	}.item().getValue("version").n().toLong()

	// The A03 item shape from the spec: one item per game, players as a list of maps, plus a version.
	private fun gameItem(gameId: String, version: Long) = mapOf(
		"id" to AttributeValue.fromS(gameId),
		"turns" to AttributeValue.fromN("20"),
		"status" to AttributeValue.fromS("LOBBY"),
		"players" to AttributeValue.fromL(
			listOf(
				AttributeValue.fromM(
					mapOf(
						"email" to AttributeValue.fromS("host@example.com"),
						"name" to AttributeValue.fromS("Host"),
						"corporationName" to AttributeValue.fromS("Host Corp")
					)
				)
			)
		),
		"version" to AttributeValue.fromN(version.toString())
	)

	// DynamoDB Local accepts any credentials; fixed dummy ones keep a developer's AWS config out of the test.
	private fun client() = DynamoDbClient.builder()
		.endpointOverride(URI("http://${container.host}:${container.getMappedPort(DYNAMODB_PORT)}"))
		.region(Region.EU_CENTRAL_1)
		.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
		.build()

	companion object {
		private const val DYNAMODB_PORT = 8000
		private const val TABLE = "star-trader-games"
		private const val PAIRS = 8
		private const val WRITES_PER_PAIR = 25

		// A01's target is ~1–2 s cross-player visibility; locally each read takes milliseconds.
		private val VISIBILITY_TARGET = Duration.ofSeconds(2)
	}
}
