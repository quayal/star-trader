package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.Factory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Factory extends this round trip in the same PR (engineering standards §12).
class FactoryItemTest {

	private val factory = Factory(system = StarSystem.SIGMA_DRACONIS, goods = Goods.FOOD, size = 4, disabledTurns = 0)

	@Test
	fun shouldReadBackEveryFieldOfAFactory() {
		assertThat(FactoryItem(FactoryItem(factory).attributes).toFactory()).isEqualTo(factory)
	}

	@Test
	fun shouldStoreAFactoryUnderTheseAttributeNames() {
		assertThat(FactoryItem(factory).attributes).isEqualTo(
			mapOf(
				"system" to AttributeValue.fromS("SIGMA_DRACONIS"),
				"goods" to AttributeValue.fromS("FOOD"),
				"size" to AttributeValue.fromN("4"),
				"disabledTurns" to AttributeValue.fromN("0")
			)
		)
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredFactoryIsIncomplete() {
		assertThatThrownBy { FactoryItem(FactoryItem(factory).attributes - "size").toFactory() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("size")
	}
}
