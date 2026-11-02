package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnPhase
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

// A story that adds a field to Turn extends this round trip in the same PR (engineering standards §12).
class TurnItemTest {

	private val offer = Offer("grace@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.SELL, 12)
	private val turn = Turn(
		number = 4,
		phase = TurnPhase.BIDDING,
		doneBidding = listOf("grace@example.com", "ada@example.com"),
		offers = listOf(offer, Offer("ada@example.com", StarSystem.BETA_HYDRI, Goods.CHIPS, OfferType.BUY, 3))
	)

	@Test
	fun shouldReadBackEveryFieldOfATurn() {
		assertThat(TurnItem(TurnItem(turn).attributes).toTurn()).isEqualTo(turn)
	}

	@Test
	fun shouldReadBackEveryPhase() {
		TurnPhase.entries.forEach { phase ->
			val inPhase = Turn(number = 4, phase = phase, doneBidding = emptyList(), offers = emptyList())
			assertThat(TurnItem(TurnItem(inPhase).attributes).toTurn()).isEqualTo(inPhase)
		}
	}

	@Test
	fun shouldStoreATurnUnderTheseAttributeNames() {
		val withOneOffer = turn.copy(offers = listOf(offer))

		assertThat(TurnItem(withOneOffer).attributes).isEqualTo(
			mapOf(
				"number" to AttributeValue.fromN("4"),
				"phase" to AttributeValue.fromS("BIDDING"),
				"doneBidding" to AttributeValue.fromL(listOf(AttributeValue.fromS("grace@example.com"), AttributeValue.fromS("ada@example.com"))),
				"offers" to AttributeValue.fromL(
					listOf(
						AttributeValue.fromM(
							mapOf(
								"playerEmail" to AttributeValue.fromS("grace@example.com"),
								"system" to AttributeValue.fromS("TAU_CETI"),
								"goods" to AttributeValue.fromS("POLY"),
								"type" to AttributeValue.fromS("SELL"),
								"price" to AttributeValue.fromN("12")
							)
						)
					)
				)
			)
		)
	}

	@Test
	fun shouldReadATurnSavedBeforeDoneBiddingAndOffersExistedAsNobodyDoneAndNoOffers() {
		val older = mapOf("number" to AttributeValue.fromN("1"), "phase" to AttributeValue.fromS("BIDDING"))

		val expected = Turn(number = 1, phase = TurnPhase.BIDDING, doneBidding = emptyList(), offers = emptyList())

		assertThat(TurnItem(older).toTurn()).isEqualTo(expected)
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredTurnIsIncomplete() {
		assertThatThrownBy { TurnItem(TurnItem(turn).attributes - "phase").toTurn() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("phase")
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredOfferIsIncomplete() {
		val attributes = TurnItem(turn.copy(offers = listOf(offer))).attributes
		val brokenOffer = OfferItem(offer).attributes - "price"

		assertThatThrownBy { TurnItem(attributes + ("offers" to AttributeValue.fromL(listOf(AttributeValue.fromM(brokenOffer))))).toTurn() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("price")
	}
}
