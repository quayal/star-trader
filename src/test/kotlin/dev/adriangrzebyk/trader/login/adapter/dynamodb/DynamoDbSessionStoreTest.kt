package dev.adriangrzebyk.trader.login.adapter.dynamodb

import dev.adriangrzebyk.trader.game.adapter.dynamodb.DynamoDbLocal
import dev.adriangrzebyk.trader.game.adapter.dynamodb.GamesTable
import dev.adriangrzebyk.trader.login.Session
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.UUID

class DynamoDbSessionStoreTest {

	private val session = Session("token-${UUID.randomUUID()}", "Adrian", "Adrian Star Traders", "adrian@example.com")

	// A fresh table per test, so tests sharing the container can't see each other's sessions.
	private val tableName = "sessions-${UUID.randomUUID()}"
	private lateinit var client: DynamoDbClient
	private lateinit var store: DynamoDbSessionStore

	@BeforeEach
	fun setUp() {
		DynamoDbLocal.assumeAvailable()
		client = DynamoDbLocal.client()
		GamesTable(client, tableName).createIfMissing()
		store = DynamoDbSessionStore(client, tableName)
	}

	@AfterEach
	fun tearDown() {
		if (::client.isInitialized) client.close()
	}

	@Test
	fun shouldFindAStoredSessionByItsToken() {
		store.store(session)

		assertThat(store.find(session.token)).isEqualTo(session)
		assertThat(store.find("some-other-token")).isNull()
	}

	@Test
	fun shouldStillFindTheSessionThroughANewStoreAsAfterARestart() {
		store.store(session)

		DynamoDbLocal.client().use { freshClient ->
			assertThat(DynamoDbSessionStore(freshClient, tableName).find(session.token)).isEqualTo(session)
		}
	}

	@Test
	fun shouldNotStoreTheTokenInTheTable() {
		store.store(session)

		val items = client.scan { it.tableName(tableName) }.items()
		assertThat(items).hasSize(1)
		assertThat(items.single().values.map { it.s() }).noneMatch { it.contains(session.token) }
	}
}
