package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.Player
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds data to Game extends this round trip in the same PR (engineering standards §12).
class GameItemMapperTest {

	private val game = Game(
		id = "game-1",
		turns = 20,
		status = GameStatus.RUNNING,
		creatorEmail = "host@example.com",
		players = listOf(
			Player(email = "host@example.com", name = "Host", corporationName = "Host Corp"),
			Player(email = "guest@example.com", name = "Guest", corporationName = "Guest Freight"),
		),
	)

	@Test
	fun shouldReadBackEveryFieldItWrote() {
		val item = GameItemMapper.toItem(game, version = 3)

		assertThat(GameItemMapper.toGame(item)).isEqualTo(game)
		assertThat(GameItemMapper.version(item)).isEqualTo(3)
	}

	@Test
	fun shouldStoreTheGameAsNativeAttributesWithPlayersAsAListOfMaps() {
		val item = GameItemMapper.toItem(game, version = 0)

		assertThat(item.keys).containsExactlyInAnyOrder("id", "turns", "status", "creatorEmail", "players", "version")
		assertThat(item.getValue("id").s()).isEqualTo("game-1")
		assertThat(item.getValue("turns").n()).isEqualTo("20")
		assertThat(item.getValue("status").s()).isEqualTo("RUNNING")
		assertThat(item.getValue("players").l().first().m()).isEqualTo(
			mapOf(
				"email" to AttributeValue.fromS("host@example.com"),
				"name" to AttributeValue.fromS("Host"),
				"corporationName" to AttributeValue.fromS("Host Corp"),
			),
		)
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredItemIsIncomplete() {
		val item = GameItemMapper.toItem(game, version = 0) - "creatorEmail"

		assertThatThrownBy { GameItemMapper.toGame(item) }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("creatorEmail")
	}
}
