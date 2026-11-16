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

	/**
	 * The region of [client]. DynamoDB Local keeps a separate set of tables per access key and region, so a Spring test
	 * must point the app's own client at the same one (`star-trader.dynamodb.region`); otherwise the app takes it from a
	 * developer's `AWS_REGION` and the test's client looks at tables that don't exist (lesson 2026-11-16).
	 */
	const val REGION = "eu-central-1"

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
			"Skipping DynamoDB Local tests: Docker is not available."
		)
	}

	// DynamoDB Local accepts any credentials; fixed dummy ones keep a developer's AWS config out of the tests.
	fun client(): DynamoDbClient = DynamoDbClient.builder()
		.endpointOverride(URI(endpoint))
		.region(Region.of(REGION))
		.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
		.build()
}
