package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Notepad
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.UUID

// R135: a notepad is one item per player per game, beside the game item, replaced whole on every save.
class DynamoDbNotepadStoreTest {

	private val tableName = "games-${UUID.randomUUID()}"
	private lateinit var client: DynamoDbClient
	private lateinit var store: DynamoDbNotepadStore

	@BeforeEach
	fun setUp() {
		DynamoDbLocal.assumeAvailable()
		client = DynamoDbLocal.client()
		GamesTable(client, tableName).createIfMissing()
		store = DynamoDbNotepadStore(client, tableName)
	}

	@AfterEach
	fun tearDown() {
		if (::client.isInitialized) client.close()
	}

	@Test
	fun shouldFindNothingForAPlayerWhoNeverSaved() {
		assertThat(store.find("game-1", "ada@example.com")).isNull()
	}

	@Test
	fun shouldReadBackWhatWasSavedIncludingLineBreaksAndUnicodeAndReplaceItOnTheNextSave() {
		store.save("game-1", "ada@example.com", Notepad("Turn 5: ★ buy\n\nturn 6: jump"))
		assertThat(store.find("game-1", "ada@example.com")).isEqualTo(Notepad("Turn 5: ★ buy\n\nturn 6: jump"))

		store.save("game-1", "ada@example.com", Notepad(""))

		assertThat(store.find("game-1", "ada@example.com")).isEqualTo(Notepad(""))
	}

	@Test
	fun shouldKeepEachPlayerAndEachGamesNotepadApart() {
		store.save("game-1", "ada@example.com", Notepad("ada, game 1"))
		store.save("game-1", "grace@example.com", Notepad("grace, game 1"))
		store.save("game-2", "ada@example.com", Notepad("ada, game 2"))

		assertThat(store.find("game-1", "ada@example.com")!!.text).isEqualTo("ada, game 1")
		assertThat(store.find("game-1", "grace@example.com")!!.text).isEqualTo("grace, game 1")
		assertThat(store.find("game-2", "ada@example.com")!!.text).isEqualTo("ada, game 2")
	}

	@Test
	fun shouldStoreAFullNotepadUnderTheItemLimit() {
		val full = Notepad("é".repeat(Notepad.MAX_LENGTH))
		store.save("game-1", "ada@example.com", full)

		assertThat(store.find("game-1", "ada@example.com")).isEqualTo(full)
	}
}
