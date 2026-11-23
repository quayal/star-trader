package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.ADA
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import dev.adriangrzebyk.trader.game.news.ActiveTipOff
import dev.adriangrzebyk.trader.game.news.News
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TakeOrder
import dev.adriangrzebyk.trader.game.news.TipOff
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R111–R114: taking opportunities on one's Opportunity turn. Kim is on turn, with Bright Reach (8 cargo spaces) at Tau Ceti.
class GameOpportunitiesTest {

	private val fixture = OpportunityGame()

	/** The Opportunity with [tipOffs] in play, in slots from 1, each learned by Kim; Bright Reach at [location] of [system]. */
	private fun with(
		vararg tipOffs: TipOff,
		system: dev.adriangrzebyk.trader.board.StarSystem = TAU_CETI,
		location: ShipLocation = ShipLocation.PLANET
	): Game {
		val inPlay = tipOffs.mapIndexed { index, it -> ActiveTipOff(index + 1, it, 1, listOf(KIM), null) }
		val game = fixture.opportunity.copy(news = News.NONE.copy(inPlay = inPlay))
		return fixture.with(game, KIM) { kim ->
			kim.copy(ships = kim.ships.map { if (it.name == "Bright Reach") it.copy(system = system, location = location) else it })
		}
	}

	private fun kim(game: Game): Player = fixture.playerOf(game, KIM)

	@Test
	fun shouldBuyIllegalGoodsOnThePlanetSecretlyAndTakeTheWholeOpportunity() {
		// O3: weapons at 10 HT on Tau Ceti's Planet, for Gamma Leporis
		val game = with(TipOff.O3)

		val bought = game.takeOpportunity(KIM, 1, TakeOrder(ship = "Bright Reach", units = 4), FakeDice(emptyList()))

		assertThat(kim(bought).money).isEqualTo(kim(game).money - 40)
		assertThat(kim(bought).ship("Bright Reach")!!.specialLots).containsExactly(SpecialLot(SpecialGoods.WEAPONS, 4, GAMMA_LEPORIS))
		assertThat(bought.news.inSlot(1).takenBy).isEqualTo(KIM)
		assertThat(bought.log).isEqualTo(game.log)
		assertThatThrownBy { game.takeOpportunity(KIM, 1, TakeOrder(ship = "Bright Reach", units = 9), FakeDice(emptyList())) }
			.hasMessage("Bright Reach has room for 8 units of weapons.")
	}

	@Test
	fun shouldRefuseAnOpportunityNotLearnedOrAlreadyTakenOrOffThePlanet() {
		val game = with(TipOff.O3)

		assertThatThrownBy {
			with(TipOff.O3, location = ShipLocation.PORT).takeOpportunity(KIM, 1, TakeOrder("Bright Reach", 1), FakeDice(emptyList()))
		}
			.hasMessage("You need a ship on Tau Ceti's Planet.")
		val taken = game.copy(news = game.news.withTipOff(game.news.inSlot(1).takenBy(ADA)))
		assertThatThrownBy { taken.takeOpportunity(KIM, 1, TakeOrder("Bright Reach", 1), FakeDice(emptyList())) }
			.hasMessage("Orion Freight already took this opportunity.")
		val unlearned = game.copy(news = News.NONE.copy(inPlay = listOf(ActiveTipOff(1, TipOff.O3, 1, emptyList(), null))))
		assertThatThrownBy { unlearned.takeOpportunity(KIM, 1, TakeOrder("Bright Reach", 1), FakeDice(emptyList())) }
			.hasMessage("Learn this tip-off first; only an opportunity can be taken.")
	}

	@Test
	fun shouldSellEveryUnitOnTheTargetPlanetsBlackMarketAtOneRoll() {
		// R112: weapons at a d6 of 3 fetch 15 each
		val game = fixture.with(with(system = GAMMA_LEPORIS), KIM) { kim ->
			kim.copy(
				ships = kim.ships.map {
					if (it.name ==
						"Bright Reach"
					) {
						it.copy(specialLots = listOf(SpecialLot(SpecialGoods.WEAPONS, 4, GAMMA_LEPORIS)))
					} else {
						it
					}
				}
			)
		}

		val sold = game.sellOnBlackMarket(KIM, GAMMA_LEPORIS, SpecialGoods.WEAPONS, FakeDice(emptyList(), listOf(3)))

		assertThat(kim(sold).money).isEqualTo(kim(game).money + 60)
		assertThat(kim(sold).ship("Bright Reach")!!.specialLots).isEmpty()
		assertThat(sold.log.last().text).isEqualTo("Kessler Cargo sold on the black market at Gamma Leporis")
	}

	@Test
	fun shouldAnnounceALegalOpportunityWithItsTaker() {
		// O5: furs in Mu Herculis's Port
		val bought = with(TipOff.O5, system = MU_HERCULIS, location = ShipLocation.PORT)
			.takeOpportunity(KIM, 1, TakeOrder(ship = "Bright Reach", units = 10), FakeDice(emptyList()))

		assertThat(bought.log.last().text).isEqualTo(
			"Kessler Cargo took the opportunity to buy furs in Mu Herculis's Port (10 HT) for sale in Beta Hydri's Port (30 HT)."
		)
		assertThat(kim(bought).ship("Bright Reach")!!.specialLots).containsExactly(SpecialLot(SpecialGoods.FURS, 10, BETA_HYDRI))
	}

	@Test
	fun shouldBuyAMilitaryShipThatAppearsOnThePlanet() {
		// O16: a Dagger and one military module at Tau Ceti; no ship needed
		val game = with(TipOff.O16, system = MU_HERCULIS)

		val bought = game.takeOpportunity(
			KIM,
			1,
			TakeOrder(name = "Stiletto", hull = Hull.DAGGER, modules = listOf(Module.DOGFIGHT), crewClass = CrewClass.B),
			FakeDice(emptyList())
		)

		val stiletto = kim(bought).ship("Stiletto")!!
		assertThat(stiletto.system).isEqualTo(TAU_CETI)
		assertThat(stiletto.location).isEqualTo(ShipLocation.PLANET)
		assertThat(kim(bought).money).isEqualTo(kim(game).money - stiletto.price())
	}

	@Test
	fun shouldFitMilitaryModulesToAShipOnThePlanet() {
		// O4: a Battle comm module on Gamma Leporis's Planet; Bright Reach is a Clarinet with all 5 modules fitted, so one comes off first
		val game = fixture.with(with(TipOff.O4, system = GAMMA_LEPORIS), KIM) { kim ->
			kim.copy(ships = kim.ships.map { if (it.name == "Bright Reach") it.copy(modules = it.modules.drop(1)) else it })
		}

		val fitted = game.takeOpportunity(KIM, 1, TakeOrder(ship = "Bright Reach", modules = listOf(Module.BATTLE_COMM)), FakeDice(emptyList()))

		assertThat(kim(fitted).ship("Bright Reach")!!.modules).contains(Module.BATTLE_COMM)
	}

	@Test
	fun shouldTakeAModuleOffToMakeRoomAndPayHalfOfItBack() {
		// Creator, 2026-11-21: Bright Reach's 5 modules fill its Clarinet, so a Cargo module comes off (2 HT back) for the
		// Battle comm (50 HT + 2 crew at class B, 20 HT)
		val game = with(TipOff.O4, system = GAMMA_LEPORIS)
		val removed = kim(game).ship("Bright Reach")!!.modules.first { it.takesModuleCapacity }

		val fitted = game.takeOpportunity(
			KIM,
			1,
			TakeOrder(ship = "Bright Reach", modules = listOf(Module.BATTLE_COMM), removed = listOf(removed)),
			FakeDice(emptyList())
		)

		val ship = kim(fitted).ship("Bright Reach")!!
		assertThat(ship.modules).containsExactlyInAnyOrderElementsOf(kim(game).ship("Bright Reach")!!.modules - removed + Module.BATTLE_COMM)
		assertThat(kim(fitted).money).isEqualTo(kim(game).money + removed.resaleValue() - Module.BATTLE_COMM.fittingCost(ship.crewClass))
	}

	@Test
	fun shouldSendTheExpeditionAndBringItBackPaid() {
		// O15: a public d6 of 3 brings it back in turn 4; a 1 would have lost it
		val game = with(TipOff.O15, location = ShipLocation.PORT)

		val sent = game.takeOpportunity(KIM, 1, TakeOrder(ship = "Bright Reach"), FakeDice(emptyList(), listOf(3)))

		assertThat(kim(sent).ship("Bright Reach")).isNull()
		assertThat(kim(sent).expeditions.single().returnTurn).isEqualTo(4)
		assertThat(sent.log.map { it.text }).contains("The Bright Reach expedition rolled 3: back in turn 4")
		val lost = game.takeOpportunity(KIM, 1, TakeOrder(ship = "Bright Reach"), FakeDice(emptyList(), listOf(1)))
		assertThat(kim(lost).expeditions).isEmpty()
		assertThat(lost.log.last().notice).isEqualTo(Notice.setback(NoticeKind.EXPEDITION_LOST, TAU_CETI, listOf(KIM)))
		val back = fixture.with(sent, KIM) { it.copy(expeditions = it.expeditions.map { e -> e.copy(returnTurn = 1) }) }
		assertThatThrownBy { back.finishOpportunities(KIM) }.hasMessage("Bring your expedition back first: choose the system it returns to.")
		val returned = back.returnExpedition(KIM, "Bright Reach", BETA_HYDRI, FakeDice(7))
		assertThat(kim(returned).ship("Bright Reach")!!.system).isEqualTo(BETA_HYDRI)
		assertThat(kim(returned).money).isEqualTo(kim(back).money + 350)
	}

	@Test
	fun shouldDeliverAJobWhenItsShipReachesTheDestinationPort() {
		// R114: O22's animals from Mu Herculis to Epsilon Eridani take a whole Passenger module
		val withSeats = kim(fixture.opportunity).ship("Bright Reach")!!.copy(modules = listOf(Module.PASSENGER, Module.CARGO))
		assertThatThrownBy { withSeats.withDelivery(TipOff.O22, Module.CARGO).withDelivery(TipOff.O22, Module.PASSENGER) }
			.hasMessage("Bright Reach already carries a delivery.")
		val carrying = withSeats.withDelivery(TipOff.O22, Module.PASSENGER)
		assertThat(carrying.freePassengerSeats()).isZero()
	}

	/** Kim's Bright Reach carrying O9's exotic plants (Gamma Leporis to Epsilon Eridani) at [location] of [system]. */
	private fun carrying(system: dev.adriangrzebyk.trader.board.StarSystem, location: ShipLocation = ShipLocation.PORT): Game =
		fixture.with(with(system = system, location = location), KIM) { kim ->
			kim.copy(ships = kim.ships.map { if (it.name == "Bright Reach") it.withDelivery(TipOff.O9, Module.CARGO) else it })
		}

	@Test
	fun shouldDeliverOnTheOpportunityTurnInTheDestinationPort() {
		// R114, R121: O9 pays 150 HT and +3 reputation
		val game = carrying(EPSILON_ERIDANI)

		val delivered = game.deliver(KIM, "Bright Reach")

		assertThat(kim(delivered).money).isEqualTo(kim(game).money + 150)
		assertThat(kim(delivered).reputation.value).isEqualTo(kim(game).reputation.value + 3)
		assertThat(kim(delivered).ship("Bright Reach")!!.deliveryJob).isNull()
		assertThat(delivered.log.map { it.text }).contains("Kessler Cargo delivered exotic plants to Epsilon Eridani")
	}

	@Test
	fun shouldRefuseToDeliverAwayFromTheDestinationPortOrOutOfTurn() {
		assertThatThrownBy { carrying(EPSILON_ERIDANI, ShipLocation.SPACE).deliver(KIM, "Bright Reach") }
			.hasMessage("Bright Reach must be in Epsilon Eridani's Port to deliver.")
		assertThatThrownBy { with().deliver(KIM, "Bright Reach") }.hasMessage("Bright Reach carries no delivery.")
		assertThatThrownBy { carrying(EPSILON_ERIDANI).deliver(ADA, "Wayfarer") }.isInstanceOf(NotYourTurnException::class.java)
	}
}
