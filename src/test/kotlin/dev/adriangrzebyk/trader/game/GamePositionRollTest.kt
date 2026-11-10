package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// E05-S06: a trade of 6 or more units rolls 2d6 for its player's market position (R43), held at once.
class GamePositionRollTest {

	private fun dice() = FakeDice.twoD6(*IntArray(MarketGood.everyOnBoard().size) { 7 })

	private val gammaLeporisPoly = MarketGood(GAMMA_LEPORIS, Goods.POLY)

	// Gamma Leporis POLY (S/D +1, 2 HT): Ada (Orion Freight) is Dictator, Kim (Altair Trade Co.) Wholesaler; Kim buys at 12
	// and Grace (Vega Lines) at 11, both with a ship in port there. Initiative: Grace, Kim, Ada.
	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
		.copy(marketPositions = listOf(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.DICTATOR)))
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Altair Trade Co.")
		.copy(marketPositions = listOf(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.WHOLESALER)))

	private val trading = Game.lobby("game-1", 3, ada)
		.addPlayer(grace)
		.addPlayer(kim)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, GAMMA_LEPORIS to MU_HERCULIS))
		.placeOffer("grace@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 11)
		.placeOffer("kim@example.com", GAMMA_LEPORIS, Goods.POLY, OfferType.BUY, 12)
		.advancePhase(FakeDice(1, 2, 1, 1, 2))
		.pickTurnOrderPlace("grace@example.com", 1)
		.pickTurnOrderPlace("kim@example.com", 2)
		.finishJumping("grace@example.com")
		.finishJumping("kim@example.com")
		.finishJumping("ada@example.com")

	// Ada passes before the roll; roll 2, counter 3: Kim's buy at 12 is reached first, up to 21 u.
	private val kimsTurnToTrade = trading.resolveNextGood("grace@example.com", dice())
		.passAsDictator("ada@example.com", FakeDice.twoD6(2))

	// Kim buys 6 and rolls 6: Wholesaler → Dictator, which demotes Ada to Wholesaler at once. Counter -3.
	private val kimPromoted = kimsTurnToTrade.trade("kim@example.com", 6, listOf(ShipLoad("Bright Reach", 6)), FakeDice.twoD6(6))

	@Test
	fun shouldRollAfterSixUnitsAndPromoteAtOnceDemotingTheDictator() {
		// S06-2, S06-3, S06-4, S06-5
		val kim = playerIn(kimPromoted, "kim@example.com")
		val ada = playerIn(kimPromoted, "ada@example.com")

		assertThat(kimPromoted.turn!!.resolution!!.trades.single().positionRoll)
			.isEqualTo(PositionRoll(6, MarketTier.DICTATOR, listOf("ada@example.com")))
		assertThat(kim.marketPositions).containsExactly(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.DICTATOR))
		assertThat(ada.marketPositions).containsExactly(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.WHOLESALER))
		assertThat(kimPromoted.log.drop(kimsTurnToTrade.log.size).map { it.text }).containsExactly(
			"Altair Trade Co. could buy up to 21 u: bought 6 u at 12 HT each, paid 72 HT",
			"Altair Trade Co. traded 6 u — rolled 6: now Dictator of POLY at Gamma Leporis; Orion Freight is demoted to Wholesaler"
		)
	}

	@Test
	fun shouldChangeNothingForTheRestOfTheResolution() {
		// S06-5: Grace's 7 u roll 3, so she's Retailer at once; trading is then over, and the wholesale step asks whom it
		// fixed when the good started resolving: Ada as Dictator, not Kim, who has an offer for it.
		val game = kimPromoted.trade("grace@example.com", 7, listOf(ShipLoad("Meridian", 7)), FakeDice.twoD6(3))

		assertThat(game.log.map { it.text }).contains("Vega Lines traded 7 u — rolled 3: now Retailer of POLY at Gamma Leporis")
		assertThat(playerIn(game, "grace@example.com").tierAt(gammaLeporisPoly)).isEqualTo(MarketTier.RETAILER)
		assertThat(game.turn!!.resolution!!.awaitingWholesaler()).isEqualTo("ada@example.com")
		assertThat(game.turn!!.resolution!!.wholesaleRole("ada@example.com")).isEqualTo("Dictator")
	}

	@Test
	fun shouldKeepThePositionsIntoTheNextTurn() {
		val next = kimPromoted.trade("grace@example.com", 7, listOf(ShipLoad("Meridian", 7)), FakeDice.twoD6(3))
			.passWholesale("ada@example.com")
			.advancePhase(dice())

		assertThat(next.turn!!.number).isEqualTo(2)
		assertThat(playerIn(next, "kim@example.com").tierAt(gammaLeporisPoly)).isEqualTo(MarketTier.DICTATOR)
		assertThat(playerIn(next, "ada@example.com").tierAt(gammaLeporisPoly)).isEqualTo(MarketTier.WHOLESALER)
		assertThat(playerIn(next, "grace@example.com").tierAt(gammaLeporisPoly)).isEqualTo(MarketTier.RETAILER)
	}

	@Test
	fun shouldChangeNothingWhenTheRollIsAboveTheUnitsTraded() {
		// S06-2, S06-4
		val game = kimPromoted.trade("grace@example.com", 7, listOf(ShipLoad("Meridian", 7)), FakeDice.twoD6(9))

		assertThat(game.log.map { it.text }).contains("Vega Lines traded 7 u — rolled 9: no change")
		assertThat(playerIn(game, "grace@example.com").marketPositions).isEmpty()
	}

	@Test
	fun shouldNotRollForFewerThanSixUnits() {
		// S06-2: one offer's trade, not summed; FakeDice fails on a roll it wasn't given
		val game = kimsTurnToTrade.trade("kim@example.com", 5, listOf(ShipLoad("Bright Reach", 5)), FakeDice.twoD6())
			.trade("grace@example.com", 5, listOf(ShipLoad("Meridian", 5)), FakeDice.twoD6())

		assertThat(game.turn!!.resolution!!.trades.map { it.positionRoll }).containsExactly(null, null)
		assertThat(game.log.map { it.text }).noneMatch { it.contains("rolled") }
	}

	@Test
	fun shouldDemoteTheDictatorPromotedEarlierThisTurnWhenAnotherBecomesDictator() {
		// S06-3: only one Dictator per good; Kim became Dictator this turn, and Grace's later roll demotes him
		val graceAsWholesaler = kimPromoted.copy(
			players = kimPromoted.players.map {
				if (it.email ==
					"grace@example.com"
				) {
					it.copy(marketPositions = listOf(MarketPosition(GAMMA_LEPORIS, Goods.POLY, MarketTier.WHOLESALER)))
				} else {
					it
				}
			}
		)

		val game = graceAsWholesaler.trade("grace@example.com", 7, listOf(ShipLoad("Meridian", 7)), FakeDice.twoD6(4))

		assertThat(game.turn!!.resolution!!.trades.last().positionRoll)
			.isEqualTo(PositionRoll(4, MarketTier.DICTATOR, listOf("kim@example.com")))
		assertThat(game.log.last { it.text.startsWith("Vega Lines traded") }.text).isEqualTo(
			"Vega Lines traded 7 u — rolled 4: now Dictator of POLY at Gamma Leporis; Altair Trade Co. is demoted to Wholesaler"
		)
		val next = game
		assertThat(
			next.players.filter {
				it.tierAt(gammaLeporisPoly) == MarketTier.DICTATOR
			}.map { it.email }
		).containsExactly("grace@example.com")
		assertThat(playerIn(next, "kim@example.com").tierAt(gammaLeporisPoly)).isEqualTo(MarketTier.WHOLESALER)
		assertThat(playerIn(next, "ada@example.com").tierAt(gammaLeporisPoly)).isEqualTo(MarketTier.WHOLESALER)
	}

	@Test
	fun shouldNotRollForTheDictatorsOwnTrade() {
		// S06-3: a Dictator can't go higher. Ada offers to buy at 13 instead of passing, so hers is reached first.
		val game = trading.resolveNextGood("grace@example.com", dice())
			.placeDictatorOffer("ada@example.com", OfferType.BUY, 13, FakeDice.twoD6(2))
			.trade("ada@example.com", 6, emptyList(), FakeDice.twoD6())

		assertThat(game.turn!!.resolution!!.trades.single().positionRoll).isNull()
		assertThat(playerIn(game, "ada@example.com").tierAt(gammaLeporisPoly)).isEqualTo(MarketTier.DICTATOR)
	}

	private fun playerIn(game: Game, email: String): Player = game.players.first { it.email == email }
}
