package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.FakeDice
import dev.adriangrzebyk.trader.game.FakeStartingSystemPicker
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.game.Player
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
}
