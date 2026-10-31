package dev.adriangrzebyk.trader.game.adapter.dynamodb

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

/**
 * One DynamoDB Local container shared by every test class in the run (Testcontainers removes it afterwards).
 * Tests that need it call [assumeAvailable] first: without Docker they are skipped locally, but CI sets CI=true,
 * so there a missing Docker fails the build instead of hiding the tests.
 */
object DynamoDbLocal {

	private const val PORT = 8000

	private val container: GenericContainer<*> by lazy {
		GenericContainer("amazon/dynamodb-local:3.3.2")
			.withExposedPorts(PORT)
			.waitingFor(Wait.forListeningPort())
			.also { it.start() }
	}

	val endpoint: String
		get() = "http://${container.host}:${container.getMappedPort(PORT)}"

	fun assumeAvailable() {
		assumeTrue(
			System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable,
			"Skipping DynamoDB Local tests: Docker is not available.",
		)
	}

	// DynamoDB Local accepts any credentials; fixed dummy ones keep a developer's AWS config out of the tests.
	fun client(): DynamoDbClient = DynamoDbClient.builder()
		.endpointOverride(URI(endpoint))
		.region(Region.EU_CENTRAL_1)
		.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
		.build()
}
