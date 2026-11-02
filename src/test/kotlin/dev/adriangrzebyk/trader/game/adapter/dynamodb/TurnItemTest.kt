package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Turn extends this round trip in the same PR (engineering standards §12).
class TurnItemTest {

	private val turn = Turn(number = 4, phase = TurnPhase.BIDDING, doneBidding = listOf("grace@example.com", "ada@example.com"))

	@Test
	fun shouldReadBackEveryFieldOfATurn() {
		assertThat(TurnItem(TurnItem(turn).attributes).toTurn()).isEqualTo(turn)
	}

	@Test
	fun shouldReadBackEveryPhase() {
		TurnPhase.entries.forEach { phase ->
			val inPhase = Turn(number = 4, phase = phase, doneBidding = emptyList())
			assertThat(TurnItem(TurnItem(inPhase).attributes).toTurn()).isEqualTo(inPhase)
		}
	}

	@Test
	fun shouldStoreATurnUnderTheseAttributeNames() {
		assertThat(TurnItem(turn).attributes).isEqualTo(
			mapOf(
				"number" to AttributeValue.fromN("4"),
				"phase" to AttributeValue.fromS("BIDDING"),
				"doneBidding" to AttributeValue.fromL(listOf(AttributeValue.fromS("grace@example.com"), AttributeValue.fromS("ada@example.com")))
			)
		)
	}

	@Test
	fun shouldReadATurnSavedBeforeDoneBiddingExistedAsNobodyDone() {
		val older = mapOf("number" to AttributeValue.fromN("1"), "phase" to AttributeValue.fromS("BIDDING"))

		assertThat(TurnItem(older).toTurn()).isEqualTo(Turn(number = 1, phase = TurnPhase.BIDDING, doneBidding = emptyList()))
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredTurnIsIncomplete() {
		assertThatThrownBy { TurnItem(TurnItem(turn).attributes - "phase").toTurn() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("phase")
	}
}
