package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.FakeDice
import dev.adriangrzebyk.trader.game.FakeStartingSystemPicker
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.Resolution
import dev.adriangrzebyk.trader.game.Trade
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GameResponseDtoTest {

	private val started = Game.lobby("game-1", 20, Player.newcomer("host@example.com", "Ada", "Orion Freight"))
		.addPlayer(Player.newcomer("guest@example.com", "Grace", "Vega Lines"))
		.start(
			"host@example.com",
			FakeStartingSystemPicker(
				StarSystem.TAU_CETI to StarSystem.BETA_HYDRI,
				StarSystem.GAMMA_LEPORIS to StarSystem.TAU_CETI
			)
		)

	@Test
	fun shouldSendEverySystemWithItsLocationsAndItsMarketInGoodsOrder() {
		val tauCeti = GameResponseDto(started, "host@example.com").board.single { it.id == "TAU_CETI" }

		assertThat(tauCeti).isEqualTo(
			SystemResponseDto(
				id = "TAU_CETI",
				name = "Tau Ceti",
				portClass = 4,
				legalSystem = 2,
				police = 3,
				security = 4,
				market = listOf(MarketEntryResponseDto("ISO", 4, -9), MarketEntryResponseDto("POLY", 6, -10))
			)
		)
	}

	@Test
	fun shouldSendEachPlayersMarketPositionsToEveryone() {
		val position = MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.DICTATOR)
		val game = started.copy(
			players = started.players.map {
				if (it.email ==
					"guest@example.com"
				) {
					it.copy(marketPositions = listOf(position))
				} else {
					it
				}
			}
		)

		val guest = GameResponseDto(game, "host@example.com").players.single { it.email == "guest@example.com" }

		assertThat(guest.marketPositions).containsExactly(MarketPositionResponseDto("MU_HERCULIS", "ISO", "DICTATOR"))
	}

	@Test
	fun shouldGiveTheViewerTheirOwnShipsCargoAndCargoSpaceUsed() {
		val host = started.players.first()
		val loaded = host.copy(
			ships = listOf(host.ships[0].copy(cargo = mapOf(Goods.POLY to 4, Goods.ISO to 3, Goods.CHIPS to 12, Goods.FOOD to 0)))
		)
		val game = started.copy(players = listOf(loaded) + started.players.drop(1))

		val you = GameResponseDto(game, "host@example.com").you!!

		assertThat(you.ships).containsExactly(YourShipResponseDto("Wayfarer", mapOf("CHIPS" to 12, "ISO" to 3, "POLY" to 4), 7, 10))
	}

	@Test
	fun shouldSendTheViewerTheirOwnOffersOnly() {
		val game = started
			.placeOffer("guest@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.SELL, 19)
			.placeOffer("host@example.com", StarSystem.BETA_HYDRI, Goods.CHIPS, OfferType.BUY, 3)

		val seenByHost = GameResponseDto(game, "host@example.com")

		assertThat(seenByHost.you!!.offers).containsExactly(YourOfferResponseDto("BETA_HYDRI", "Beta Hydri", "CHIPS", "BUY", 3))
		assertThat(seenByHost.toString()).doesNotContain("SELL").doesNotContain("price=19")
	}

	@Test
	fun shouldSendTheViewerTheirOwnBidOnlyAndNobodyElsesMoney() {
		val game = started.placeInitiativeBid("guest@example.com", 137).placeInitiativeBid("host@example.com", 5)

		val seenByHost = GameResponseDto(game, "host@example.com")

		assertThat(seenByHost.you!!.bid).isEqualTo(5)
		assertThat(seenByHost.toString()).doesNotContain("137")
	}

	@Test
	fun shouldSendTheInitiativeOrderToEveryoneOnceBiddingCloses() {
		val closed = started.advancePhase(FakeDice(2, 1))

		assertThat(GameResponseDto(closed, "guest@example.com").turn!!.initiative).containsExactly("host@example.com", "guest@example.com")
		assertThat(GameResponseDto(started, "guest@example.com").turn!!.initiative).isEmpty()
	}

	@Test
	fun shouldSendNoOffersBeforeTheGameStarts() {
		val lobby = Game.lobby("game-2", 20, Player.newcomer("host@example.com", "Ada", "Orion Freight"))

		assertThat(GameResponseDto(lobby, "host@example.com").you!!.offers).isEmpty()
	}

	@Test
	fun shouldSendTheCounterAndEachTradeOfTheResolutionToEveryone() {
		// E05-S03, E05-S04-6: public, like the disclosed offers: what the market allowed and what took place
		val vega = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)
		val orion = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 10)
		val resolution = Resolution(
			MarketGood(StarSystem.MU_HERCULIS, Goods.ISO),
			11,
			listOf(vega, orion),
			null,
			false,
			3,
			listOf(Trade(vega, 4, 4), Trade(orion, 5, 0)),
			emptyList(),
			emptyList(),
			emptyList()
		)

		val dto = ResolutionResponseDto(resolution)

		assertThat(dto.startingCounter).isEqualTo(3)
		assertThat(dto.trades).containsExactly(
			TradeResponseDto("vega@example.com", "BUY", 11, 4, 4),
			TradeResponseDto("orion@example.com", "SELL", 10, 5, 0)
		)
		assertThat(dto.awaitingTrade).isNull()
	}

	// E05-S04: Grace (guest) buys ISO at 11 at Mu Herculis; the counter is at 3, so up to 4 u, and the game waits for her.
	private val gracesBuy = Offer("guest@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)
	private val awaitingGrace =
		Resolution(
			MarketGood(
				StarSystem.MU_HERCULIS,
				Goods.ISO
			),
			11, listOf(gracesBuy), null, false, 3, emptyList(), emptyList(), emptyList(), emptyList()
		)

	@Test
	fun shouldTellEveryoneWhoTheGameWaitsForAndWhatTheMarketAllows() {
		// S01-8, D09
		assertThat(ResolutionResponseDto(awaitingGrace).awaitingTrade).isEqualTo(AwaitingTradeResponseDto("guest@example.com", "BUY", 11, 4))
	}

	@Test
	fun shouldSendTheTradersOwnMaxOnlyToThem() {
		// S04-1, S04-2, R29: Grace has 30 HT, so 2 u at 11 HT; her max depends on her money, so nobody else sees it. With
		// 5 HT it's 0, and she acknowledges trading nothing (S04-5).
		val withMoney = { money: Int ->
			started.copy(
				players = started.players.map { if (it.email == "guest@example.com") it.pay(300 - money) else it },
				turn = started.turn!!.copy(resolution = awaitingGrace)
			)
		}

		assertThat(GameResponseDto(withMoney(30), "guest@example.com").you!!.tradeMax).isEqualTo(2)
		assertThat(GameResponseDto(withMoney(5), "guest@example.com").you!!.tradeMax).isZero()
		assertThat(GameResponseDto(withMoney(30), "host@example.com").you!!.tradeMax).isNull()
		assertThat(GameResponseDto(started, "guest@example.com").you!!.tradeMax).isNull()
	}
}
