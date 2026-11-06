package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PlayerTest {

	private val wayfarer = Ship.emptyAt("Wayfarer", StarSystem.TAU_CETI, ShipLocation.PORT)
	private val lantern = Ship.emptyAt("Lantern", StarSystem.BETA_HYDRI, ShipLocation.PORT)
	private val player = Player.newcomer("ada@example.com", "Ada", "Orion Freight").startWith(300, listOf(wayfarer, lantern))

	@Test
	fun shouldMoveOnlyTheShipThatJumped() {
		val moved = player.jumpShip(wayfarer, StarSystem.MU_HERCULIS)

		assertThat(moved.ships).containsExactly(Ship.emptyAt("Wayfarer", StarSystem.MU_HERCULIS, ShipLocation.SPACE), lantern)
	}

	@Test
	fun shouldLandTheNamedShip() {
		val landed = player.jumpShip(wayfarer, StarSystem.MU_HERCULIS).landShip("Wayfarer", ShipLocation.PLANET)

		assertThat(landed.ship("Wayfarer")).isEqualTo(Ship.emptyAt("Wayfarer", StarSystem.MU_HERCULIS, ShipLocation.PLANET))
	}

	@Test
	fun shouldBidUpToAllTheirMoney() {
		assertThat(player.bid(300)).isEqualTo(InitiativeBid("ada@example.com", 300))
	}

	@Test
	fun shouldRefuseABidAboveTheirMoney() {
		assertThatThrownBy { player.bid(301) }
			.isInstanceOf(InvalidBidException::class.java)
			.hasMessage("You have 300 HT. Enter 300 or less.")
	}

	@Test
	fun shouldBeDictatorOnlyOfTheGoodAndSystemTheyHoldIt() {
		val dictator = player.copy(marketPositions = listOf(MarketPosition(StarSystem.TAU_CETI, Goods.POLY, MarketTier.DICTATOR)))

		assertThat(dictator.isDictatorOf(StarSystem.TAU_CETI, Goods.POLY)).isTrue()
		assertThat(dictator.isDictatorOf(StarSystem.TAU_CETI, Goods.ISO)).isFalse()
		assertThat(dictator.isDictatorOf(StarSystem.BETA_HYDRI, Goods.POLY)).isFalse()
	}

	@Test
	fun shouldNotOfferAGoodTheyAreDictatorOfThere() {
		// R38: the Dictator places that offer when it resolves in Trading.
		val dictator = player.copy(marketPositions = listOf(MarketPosition(StarSystem.TAU_CETI, Goods.POLY, MarketTier.DICTATOR)))

		assertThatThrownBy { dictator.offer(StarSystem.TAU_CETI, Goods.POLY, OfferType.SELL, 9) }
			.isInstanceOf(InvalidOfferException::class.java)
			.hasMessage("You're Dictator of POLY here — you place that offer when it resolves in Trading.")
		assertThat(dictator.offer(StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 9))
			.isEqualTo(Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 9))
	}

	@Test
	fun shouldListStartingPositionsForTheLog() {
		assertThat(player.startingPositions()).isEqualTo("Orion Freight — Wayfarer at Tau Ceti, Lantern at Beta Hydri")
	}
}
