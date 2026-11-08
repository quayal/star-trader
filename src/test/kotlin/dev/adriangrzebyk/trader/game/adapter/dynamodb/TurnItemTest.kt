package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.InitiativeBid
import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.game.Resolution
import dev.adriangrzebyk.trader.game.Trade
import dev.adriangrzebyk.trader.game.Turn
import dev.adriangrzebyk.trader.game.TurnOrderPick
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
		offers = listOf(offer, Offer("ada@example.com", StarSystem.BETA_HYDRI, Goods.CHIPS, OfferType.BUY, 3)),
		bids = listOf(InitiativeBid("grace@example.com", 40), InitiativeBid("ada@example.com", 0)),
		initiative = listOf("ada@example.com", "grace@example.com"),
		turnOrderPicks = listOf(TurnOrderPick("ada@example.com", 2), TurnOrderPick("grace@example.com", 1)),
		jumpedShips = listOf("Wayfarer", "Meridian"),
		doneJumping = listOf("ada@example.com"),
		landingShip = "Meridian",
		tradingQueue = listOf(MarketGood(StarSystem.MU_HERCULIS, Goods.ISO), MarketGood(StarSystem.TAU_CETI, Goods.POLY)),
		resolvedGoods = listOf(MarketGood(StarSystem.GAMMA_LEPORIS, Goods.POLY)),
		resolution = Resolution(
			MarketGood(StarSystem.TAU_CETI, Goods.POLY),
			listOf(offer, Offer("kim@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.BUY, 9)),
			"kim@example.com",
			true,
			-6,
			listOf(Trade(Offer("kim@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.BUY, 9), 5, 3), Trade(offer, 2, 0)),
			listOf("grace@example.com", "ada@example.com")
		)
	)

	@Test
	fun shouldReadBackEveryFieldOfATurn() {
		assertThat(TurnItem(TurnItem(turn).attributes).toTurn()).isEqualTo(turn)
	}

	@Test
	fun shouldReadBackAResolutionWithNoDictatorAndOneStillWaitingForTheirs() {
		val good = MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)
		val noDictator = Resolution(good, emptyList(), null, false, null, emptyList(), emptyList())
		val waiting = Resolution(good, listOf(offer), "kim@example.com", false, null, emptyList(), emptyList())
		listOf(noDictator, waiting).forEach { resolution ->
			val withResolution = turn.copy(resolution = resolution)
			assertThat(TurnItem(TurnItem(withResolution).attributes).toTurn()).isEqualTo(withResolution)
		}
	}

	@Test
	fun shouldReadBackEveryPhase() {
		TurnPhase.entries.forEach { phase ->
			val inPhase =
				Turn(
					number = 4,
					phase = phase,
					doneBidding = emptyList(),
					offers = emptyList(),
					bids = emptyList(),
					initiative = emptyList(),
					turnOrderPicks = emptyList(),
					jumpedShips = emptyList(),
					doneJumping = emptyList(),
					landingShip = null,
					tradingQueue = emptyList(),
					resolvedGoods = emptyList(),
					resolution = null
				)
			assertThat(TurnItem(TurnItem(inPhase).attributes).toTurn()).isEqualTo(inPhase)
		}
	}

	@Test
	fun shouldStoreATurnUnderTheseAttributeNames() {
		val withOneOfEach = turn.copy(
			offers = listOf(offer),
			bids = listOf(InitiativeBid("grace@example.com", 40)),
			turnOrderPicks = listOf(TurnOrderPick("ada@example.com", 2)),
			jumpedShips = listOf("Wayfarer"),
			tradingQueue = listOf(MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)),
			resolvedGoods = emptyList(),
			resolution = Resolution(
				MarketGood(StarSystem.TAU_CETI, Goods.POLY),
				listOf(offer),
				"kim@example.com",
				false,
				null,
				emptyList(),
				emptyList()
			)
		)

		assertThat(TurnItem(withOneOfEach).attributes).isEqualTo(
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
				),
				"bids" to AttributeValue.fromL(
					listOf(
						AttributeValue.fromM(
							mapOf("playerEmail" to AttributeValue.fromS("grace@example.com"), "amount" to AttributeValue.fromN("40"))
						)
					)
				),
				"initiative" to AttributeValue.fromL(listOf(AttributeValue.fromS("ada@example.com"), AttributeValue.fromS("grace@example.com"))),
				"turnOrderPicks" to AttributeValue.fromL(
					listOf(AttributeValue.fromM(mapOf("playerEmail" to AttributeValue.fromS("ada@example.com"), "place" to AttributeValue.fromN("2"))))
				),
				"jumpedShips" to AttributeValue.fromL(listOf(AttributeValue.fromS("Wayfarer"))),
				"doneJumping" to AttributeValue.fromL(listOf(AttributeValue.fromS("ada@example.com"))),
				"landingShip" to AttributeValue.fromS("Meridian"),
				"tradingQueue" to AttributeValue.fromL(
					listOf(AttributeValue.fromM(mapOf("system" to AttributeValue.fromS("MU_HERCULIS"), "goods" to AttributeValue.fromS("ISO"))))
				),
				"resolvedGoods" to AttributeValue.fromL(emptyList()),
				"resolution" to AttributeValue.fromM(
					mapOf(
						"good" to AttributeValue.fromM(mapOf("system" to AttributeValue.fromS("TAU_CETI"), "goods" to AttributeValue.fromS("POLY"))),
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
						),
						"dictator" to AttributeValue.fromS("kim@example.com"),
						"dictatorDecided" to AttributeValue.fromBool(false),
						"trades" to AttributeValue.fromL(emptyList()),
						"initiative" to AttributeValue.fromL(emptyList())
					)
				)
			)
		)
	}

	@Test
	fun shouldStoreNoResolutionBeforeTheFirstAndNoDictatorWhenTheGoodHasNone() {
		// An item saved before E05-S02 reads the same way: no resolution waits for anyone (BACKWARD_COMPATIBILITY.md §7).
		assertThat(TurnItem(turn.copy(resolution = null)).attributes).doesNotContainKey("resolution")
		assertThat(TurnItem(TurnItem(turn).attributes - "resolution").toTurn().resolution).isNull()
		val noDictator = Resolution(MarketGood(StarSystem.TAU_CETI, Goods.POLY), emptyList(), null, false, null, emptyList(), emptyList())
		assertThat(TurnItem(turn.copy(resolution = noDictator)).attributes.getValue("resolution").m()).doesNotContainKey("dictator")
	}

	@Test
	fun shouldStoreTheCounterAndTradesUnderTheseAttributeNames() {
		val resolution = TurnItem(turn).attributes.getValue("resolution").m()

		assertThat(resolution.getValue("startingCounter")).isEqualTo(AttributeValue.fromN("-6"))
		assertThat(resolution.getValue("trades").l().first().m().keys).containsExactlyInAnyOrder("offer", "marketMax", "units")
		assertThat(resolution.getValue("trades").l().first().m().getValue("marketMax")).isEqualTo(AttributeValue.fromN("5"))
		assertThat(resolution.getValue("trades").l().first().m().getValue("units")).isEqualTo(AttributeValue.fromN("3"))
		assertThat(resolution.getValue("initiative").l().map { it.s() }).containsExactly("grace@example.com", "ada@example.com")
	}

	@Test
	fun shouldReadAResolutionSavedBeforeTradersChoseAmountsWithNoInitiativeAndEachTradeAtTheMarketsMax() {
		// E05-S03 ran the counter to its end, every offer trading the most the market allowed, so nothing waits and no tie
		// is left to break (BACKWARD_COMPATIBILITY.md §7).
		val stored = TurnItem(turn).attributes.getValue("resolution").m()
		val olderTrades = stored.getValue("trades").l().map { AttributeValue.fromM(it.m() - "marketMax") }
		val older = TurnItem(
			TurnItem(turn).attributes +
				("resolution" to AttributeValue.fromM(stored - "initiative" + ("trades" to AttributeValue.fromL(olderTrades))))
		).toTurn()

		assertThat(older.resolution!!.initiative).isEmpty()
		assertThat(older.resolution!!.trades.map { it.marketMax }).containsExactly(3, 0)
	}

	@Test
	fun shouldStoreNoCounterBeforeItStartsAndReadAResolutionSavedBeforeTheCounterAsNothingTraded() {
		// A resolution saved before E05-S03 finished without a counter, so it reads with none and no trades
		// (BACKWARD_COMPATIBILITY.md §7).
		val waiting =
			Resolution(MarketGood(StarSystem.TAU_CETI, Goods.POLY), emptyList(), "kim@example.com", false, null, emptyList(), emptyList())
		assertThat(TurnItem(turn.copy(resolution = waiting)).attributes.getValue("resolution").m()).doesNotContainKey("startingCounter")
		val stored = TurnItem(turn).attributes.getValue("resolution").m()
		val older = TurnItem(TurnItem(turn).attributes + ("resolution" to AttributeValue.fromM(stored - "startingCounter" - "trades"))).toTurn()
		assertThat(older.resolution!!.startingCounter).isNull()
		assertThat(older.resolution!!.trades).isEmpty()
		assertThat(older.resolution!!.isFinished()).isTrue()
	}

	@Test
	fun shouldStoreNoLandingShipWhenNoShipWaitsToLand() {
		val attributes = TurnItem(turn.copy(landingShip = null)).attributes

		assertThat(attributes).doesNotContainKey("landingShip")
		assertThat(TurnItem(attributes).toTurn().landingShip).isNull()
	}

	@Test
	fun shouldReadATurnSavedBeforeDoneBiddingOffersBidsPicksAndJumpsExistedAsNobodyDoneAndNothingPlaced() {
		val older = mapOf("number" to AttributeValue.fromN("1"), "phase" to AttributeValue.fromS("BIDDING"))

		val expected =
			Turn(
				number = 1,
				phase = TurnPhase.BIDDING,
				doneBidding = emptyList(),
				offers = emptyList(),
				bids = emptyList(),
				initiative = emptyList(),
				turnOrderPicks = emptyList(),
				jumpedShips = emptyList(),
				doneJumping = emptyList(),
				landingShip = null,
				tradingQueue = emptyList(),
				resolvedGoods = emptyList(),
				resolution = null
			)

		assertThat(TurnItem(older).toTurn()).isEqualTo(expected)
	}

	@Test
	fun shouldReadATurnSavedInTradingBeforeTheQueueExistedWithEveryGoodStillToResolve() {
		// Nothing could resolve before E05-S01, so every good is still queued (BACKWARD_COMPATIBILITY.md §7).
		val older = mapOf("number" to AttributeValue.fromN("3"), "phase" to AttributeValue.fromS("TRADING"))

		val read = TurnItem(older).toTurn()

		assertThat(read.tradingQueue).isEqualTo(MarketGood.everyOnBoard())
		assertThat(read.resolvedGoods).isEmpty()
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

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredBidIsIncomplete() {
		val brokenBid = InitiativeBidItem(InitiativeBid("grace@example.com", 40)).attributes - "amount"

		assertThatThrownBy {
			TurnItem(TurnItem(turn).attributes + ("bids" to AttributeValue.fromL(listOf(AttributeValue.fromM(brokenBid))))).toTurn()
		}
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("amount")
	}

	@Test
	fun shouldNameTheMissingAttributeWhenAStoredPickIsIncomplete() {
		val brokenPick = TurnOrderPickItem(TurnOrderPick("grace@example.com", 1)).attributes - "place"

		assertThatThrownBy {
			TurnItem(TurnItem(turn).attributes + ("turnOrderPicks" to AttributeValue.fromL(listOf(AttributeValue.fromM(brokenPick))))).toTurn()
		}
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("place")
	}
}
