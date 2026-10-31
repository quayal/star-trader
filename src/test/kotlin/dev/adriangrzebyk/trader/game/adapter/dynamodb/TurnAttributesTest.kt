package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Turn extends this round trip in the same PR (engineering standards §12).
class TurnAttributesTest {

	private val turn = Turn(number = 4, phase = TurnPhase.BIDDING)

	@Test
	fun shouldReadBackEveryFieldOfATurn() {
		assertThat(TurnAttributes.from(TurnAttributes.toAttributes(turn))).isEqualTo(turn)
	}

	@Test
	fun shouldStoreATurnUnderTheseAttributeNames() {
		assertThat(TurnAttributes.toAttributes(turn)).isEqualTo(
			mapOf("number" to AttributeValue.fromN("4"), "phase" to AttributeValue.fromS("BIDDING")),
		)
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredTurnIsIncomplete() {
		assertThatThrownBy { TurnAttributes.from(TurnAttributes.toAttributes(turn) - "phase") }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("phase")
	}
}
