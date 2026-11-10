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
	fun shouldBeWholesalerOnlyOfTheGoodAndSystemTheyHoldIt() {
		// R28, R37
		val wholesaler = player.copy(marketPositions = listOf(MarketPosition(StarSystem.TAU_CETI, Goods.POLY, MarketTier.WHOLESALER)))

		assertThat(wholesaler.isWholesalerOf(MarketGood(StarSystem.TAU_CETI, Goods.POLY))).isTrue()
		assertThat(wholesaler.isWholesalerOf(MarketGood(StarSystem.TAU_CETI, Goods.ISO))).isFalse()
		assertThat(wholesaler.isDictatorOf(StarSystem.TAU_CETI, Goods.POLY)).isFalse()
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

	@Test
	fun shouldCapABuyAtWhatTheyCanAfford() {
		// R12: 300 HT at 20 HT each affords 15.
		val buy = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 20)

		assertThat(player.tradeMax(buy, 16)).isEqualTo(15)
		assertThat(player.tradeMax(buy, 4)).isEqualTo(4)
		assertThat(player.pay(290).tradeMax(buy, 4)).isZero()
	}

	@Test
	fun shouldCapASellAtWhatTheirShipsAtThatSystemHold() {
		// R10, R42: only ships in port trade. Wayfarer is in port at Tau Ceti; Meridian is there but in space; Lantern is elsewhere.
		val trader = player.copy(
			ships = listOf(
				wayfarer.load(Goods.ISO, 3),
				lantern.load(Goods.ISO, 9),
				Ship.emptyAt("Meridian", StarSystem.TAU_CETI, ShipLocation.SPACE).load(Goods.ISO, 2)
			)
		)
		val sell = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 9)

		assertThat(trader.tradeMax(sell, 8)).isEqualTo(3)
		assertThat(player.tradeMax(sell, 8)).isZero()
	}

	@Test
	fun shouldNeverCapBelowZero() {
		val buy = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 10)

		assertThat(player.tradeMax(buy, -2)).isZero()
	}

	@Test
	fun shouldPayForABuyAndLoadWhatFitsLosingTheRest() {
		// S04-4: Wayfarer has 10 free spaces and Meridian 1; 12 ISO split 10 + 2 onto them fit 10 + 1, and 1 is lost.
		val meridian = Ship("Meridian", StarSystem.TAU_CETI, ShipLocation.PORT, mapOf(Goods.POLY to 9))
		val trader = player.copy(ships = listOf(wayfarer, meridian))
		val buy = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 10)

		val traded = trader.trade(buy, 14, 12, listOf(ShipLoad("Wayfarer", 10), ShipLoad("Meridian", 2)))

		assertThat(traded.money).isEqualTo(180)
		assertThat(traded.ship("Wayfarer")?.holding(Goods.ISO)).isEqualTo(10)
		assertThat(traded.ship("Meridian")?.holding(Goods.ISO)).isEqualTo(1)
	}

	@Test
	fun shouldLoseEverythingBoughtWithNoShipThere() {
		// S04-4, R23
		val buy = Offer("ada@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 10)

		val traded = player.trade(buy, 4, 3, emptyList())

		assertThat(traded.money).isEqualTo(270)
		assertThat(traded.ships).containsExactly(wayfarer, lantern)
	}

	@Test
	fun shouldBePaidForASellAndUnloadIt() {
		val trader = player.copy(ships = listOf(wayfarer.load(Goods.CHIPS, 7), lantern))
		val sell = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.CHIPS, OfferType.SELL, 9)

		val traded = trader.trade(sell, 5, 5, listOf(ShipLoad("Wayfarer", 5)))

		assertThat(traded.money).isEqualTo(345)
		assertThat(traded.ship("Wayfarer")?.holding(Goods.CHIPS)).isEqualTo(2)
	}

	@Test
	fun shouldRefuseAnAmountOutsideOneToTheirMax() {
		// S04-1
		val buy = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 10)

		for (amount in listOf(null, 0, 5)) {
			assertThatThrownBy { player.trade(buy, 4, amount, listOf(ShipLoad("Wayfarer", amount ?: 0))) }
				.isInstanceOf(InvalidTradeException::class.java)
				.hasMessage("Enter a whole number from 1 to 4.")
		}
	}

	@Test
	fun shouldSayWhyTheyCanTradeNothingWhenTheirMaxIsZero() {
		// S04-5, E05-S05-4: a wholesale reaches Player.trade even when they can't trade a unit; they're told why
		val buy = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 20)
		val sell = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 9)

		assertThatThrownBy { player.pay(290).trade(buy, 5, 1, listOf(ShipLoad("Wayfarer", 1))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("You can't afford any units at 20 HT.")
		assertThatThrownBy { player.trade(sell, 5, 1, listOf(ShipLoad("Wayfarer", 1))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("You have no ISO in your ships in port at Tau Ceti, so you can't sell any.")
	}

	@Test
	fun shouldRefuseASplitThatDoesNotAddUpOrNamesAShipElsewhere() {
		// S04-3
		val buy = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 10)

		assertThatThrownBy { player.trade(buy, 4, 4, listOf(ShipLoad("Wayfarer", 3))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Your ships' units must add up to 4.")
		assertThatThrownBy { player.trade(buy, 4, 4, emptyList()) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Your ships' units must add up to 4.")
		assertThatThrownBy { player.trade(buy, 4, 4, listOf(ShipLoad("Lantern", 4))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Choose your ships in port at Tau Ceti.")
		assertThatThrownBy { player.trade(buy, 4, 4, listOf(ShipLoad("Wayfarer", 5), ShipLoad("Wayfarer", -1))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Enter a whole number of units, 0 or more, for each ship.")
	}

	@Test
	fun shouldRefuseToSellMoreFromAShipThanItHolds() {
		// S04-3, R10: 5 ISO at Tau Ceti in all, but Wayfarer holds only 2.
		val trader = player.copy(
			ships = listOf(wayfarer.load(Goods.ISO, 2), Ship.emptyAt("Meridian", StarSystem.TAU_CETI, ShipLocation.PORT).load(Goods.ISO, 3))
		)
		val sell = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.SELL, 9)

		assertThatThrownBy { trader.trade(sell, 5, 3, listOf(ShipLoad("Wayfarer", 3), ShipLoad("Meridian", 0))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Wayfarer has only 2 ISO.")
	}

	@Test
	fun shouldTradeOnlyThroughShipsInPort() {
		// R23, R42: trading happens in port. Wayfarer is at Tau Ceti but on the planet, so it can't load, and with no ship
		// in port a buy's goods are lost.
		val trader = player.copy(ships = listOf(wayfarer.landAt(ShipLocation.PLANET), lantern))
		val buy = Offer("ada@example.com", StarSystem.TAU_CETI, Goods.ISO, OfferType.BUY, 10)

		assertThat(trader.shipsInPortAt(StarSystem.TAU_CETI)).isEmpty()
		assertThatThrownBy { trader.trade(buy, 4, 2, listOf(ShipLoad("Wayfarer", 2))) }
			.isInstanceOf(InvalidTradeException::class.java)
			.hasMessage("Choose your ships in port at Tau Ceti.")
		assertThat(trader.trade(buy, 4, 2, emptyList()).ship("Wayfarer")!!.holding(Goods.ISO)).isZero()
	}

	private val muHerculisIso = MarketGood(StarSystem.MU_HERCULIS, Goods.ISO)

	@Test
	fun shouldRiseOneTierFromTheNextTurnAfterAWonPositionRoll() {
		// E05-S06-2, S06-5: none → Retailer → Wholesaler → Dictator, from the tier held this turn
		val retailer = player.promotedAt(muHerculisIso)
		val wholesaler = retailer.applyPendingPositions().promotedAt(muHerculisIso)
		val dictator = wholesaler.applyPendingPositions().promotedAt(muHerculisIso)

		assertThat(retailer.tierAt(muHerculisIso)).isNull()
		assertThat(retailer.pendingPositions).containsExactly(MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.RETAILER))
		assertThat(wholesaler.pendingPositions.single().tier).isEqualTo(MarketTier.WHOLESALER)
		assertThat(dictator.willBeDictatorOf(muHerculisIso)).isTrue()
		assertThat(dictator.isDictatorOf(StarSystem.MU_HERCULIS, Goods.ISO)).isFalse()
		assertThat(dictator.applyPendingPositions().marketPositions)
			.containsExactly(MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.DICTATOR))
		assertThat(dictator.applyPendingPositions().canRiseAt(muHerculisIso)).isFalse()
	}

	@Test
	fun shouldKeepPositionsForOtherGoodsAndSystemsWhenOneChanges() {
		// R28: a position is for one good at one system
		val tauCetiPoly = MarketPosition(StarSystem.TAU_CETI, Goods.POLY, MarketTier.WHOLESALER)
		val holder = player.copy(marketPositions = listOf(tauCetiPoly, MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.RETAILER)))

		val next = holder.promotedAt(muHerculisIso).applyPendingPositions()

		assertThat(
			next.marketPositions
		).containsExactlyInAnyOrder(tauCetiPoly, MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.WHOLESALER))
		assertThat(next.pendingPositions).isEmpty()
	}

	@Test
	fun shouldBeWholesalerFromTheNextTurnOnceDemotedAndDropAPendingDictatorship() {
		// E05-S06-3
		val dictator = player.copy(marketPositions = listOf(MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.DICTATOR)))
		val wholesaler = player.copy(marketPositions = listOf(MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.WHOLESALER)))

		assertThat(dictator.demotedAt(muHerculisIso).willBeDictatorOf(muHerculisIso)).isFalse()
		assertThat(dictator.demotedAt(muHerculisIso).isDictatorOf(StarSystem.MU_HERCULIS, Goods.ISO)).isTrue()
		assertThat(wholesaler.promotedAt(muHerculisIso).demotedAt(muHerculisIso).pendingPositions).isEmpty()
	}
}
