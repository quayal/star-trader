package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.MarketGood
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class MarketGoodItemTest {

	@Test
	fun shouldReadBackEveryGoodOnTheBoard() {
		MarketGood.everyOnBoard().forEach { good ->
			assertThat(MarketGoodItem(MarketGoodItem(good).attributes).toMarketGood()).isEqualTo(good)
		}
	}

	@Test
	fun shouldStoreAMarketGoodUnderTheseAttributeNames() {
		assertThat(MarketGoodItem(MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)).attributes).isEqualTo(
			mapOf("system" to AttributeValue.fromS("MU_HERCULIS"), "goods" to AttributeValue.fromS("ISO"))
		)
	}
}
