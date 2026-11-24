package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.ADA
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import dev.adriangrzebyk.trader.game.news.SpecialGoods
import dev.adriangrzebyk.trader.game.news.SpecialLot
import dev.adriangrzebyk.trader.game.news.TipOff
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R125: passengers in Opportunity. Kim is on turn with Bright Reach (two Passenger modules: 4 seats) in Tau Ceti's Port.
class GamePassengersTest {

	private val fixture = OpportunityGame()
	private val tauToBeta = PassengerRoute(TAU_CETI, BETA_HYDRI)

	/** [game] with [email]'s ship [name] fitted with two Passenger modules, in the Port of [system]. */
	private fun seated(game: Game, email: String, name: String, system: dev.adriangrzebyk.trader.board.StarSystem): Game =
		fixture.with(game, email) { player ->
			player.copy(
				ships = player.ships.map {
					if (it.name ==
						name
					) {
						it.copy(system = system, location = ShipLocation.PORT, modules = listOf(Module.PASSENGER, Module.PASSENGER))
					} else {
						it
					}
				}
			)
		}

	private val opportunity = seated(fixture.opportunity, KIM, "Bright Reach", TAU_CETI)

	private fun kim(game: Game): Player = fixture.playerOf(game, KIM)

	@Test
	fun shouldOfferTheTwoPortClassesAddedOnEveryRoute() {
		assertThat(tauToBeta.groups()).isEqualTo(7)
		assertThat(PassengerRoute(MU_HERCULIS, TAU_CETI).groups()).isEqualTo(3)
		assertThat(PassengerRoute.all()).hasSize(30)
		assertThat(opportunity.passengerGroupsLeft(tauToBeta)).isEqualTo(7)
	}

	@Test
	fun shouldPayTheFareAtOnceTakeSeatsAndLogItPublicly() {
		val boarded = opportunity.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 3)

		assertThat(kim(boarded).money).isEqualTo(kim(opportunity).money + 15)
		assertThat(kim(boarded).passengerGroups).containsExactly(PassengerGroup("Bright Reach", TAU_CETI, BETA_HYDRI, 3))
		assertThat(kim(boarded).freeSeatsOn(kim(boarded).ship("Bright Reach")!!)).isEqualTo(1)
		assertThat(boarded.passengerGroupsLeft(tauToBeta)).isEqualTo(4)
		assertThat(boarded.log.map { it.text }).contains("Kessler Cargo took 3 passenger groups from Tau Ceti to Beta Hydri.")
	}

	@Test
	fun shouldLeaveOnlyWhatTheFirstTakerLeftToTheNextPlayer() {
		val kimTook = opportunity.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 4).finishOpportunities(KIM)
		val adaReady = seated(kimTook, ADA, "Wayfarer", TAU_CETI)

		assertThatThrownBy { adaReady.takePassengers(ADA, "Wayfarer", BETA_HYDRI, 4) }
			.hasMessage("Only 3 passenger groups are left from Tau Ceti to Beta Hydri.")
		val adaTook = adaReady.takePassengers(ADA, "Wayfarer", BETA_HYDRI, 3)
		assertThat(adaTook.passengerGroupsLeft(tauToBeta)).isZero()
		assertThatThrownBy { adaTook.takePassengers(ADA, "Wayfarer", BETA_HYDRI, 1) }
			.hasMessage("Only 0 passenger groups are left from Tau Ceti to Beta Hydri.")
	}

	@Test
	fun shouldRefuseWithoutSeatsOutsideThePortOrWithABadOrder() {
		assertThatThrownBy {
			opportunity.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 5)
		}.hasMessage("Bright Reach has 4 free passenger seats.")
		assertThatThrownBy { opportunity.takePassengers(KIM, "Bright Reach", TAU_CETI, 1) }.hasMessage("Passengers go to another system.")
		assertThatThrownBy { opportunity.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 0) }.hasMessage("Take at least 1 passenger group.")
		assertThatThrownBy { opportunity.takePassengers(KIM, "Bright Reach", null, 1) }.hasMessage("Choose where the passengers go.")
		assertThatThrownBy { opportunity.takePassengers(KIM, "Nobody", BETA_HYDRI, 1) }.hasMessage("Choose one of your ships.")
		val inSpace = fixture.with(opportunity, KIM) { kim ->
			kim.copy(ships = kim.ships.map { if (it.name == "Bright Reach") it.copy(location = ShipLocation.SPACE) else it })
		}
		assertThatThrownBy { inSpace.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 1) }
			.hasMessage("Bright Reach must be in Tau Ceti's Port to take passengers.")
		assertThatThrownBy { opportunity.takePassengers(ADA, "Wayfarer", BETA_HYDRI, 1) }.isInstanceOf(NotYourTurnException::class.java)
	}

	@Test
	fun shouldNeedFreeSeatsNotTakenByOtherPassengers() {
		val boarded = opportunity.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 3)

		assertThatThrownBy { boarded.takePassengers(KIM, "Bright Reach", MU_HERCULIS, 2) }.hasMessage("Bright Reach has 1 free passenger seats.")
	}

	@Test
	fun shouldChargeEveryUndeliveredGroupWhateverTheReasonAndNeverReturnThemToThePool() {
		// Corvid (Beta Hydri's Port, 2 working seats of 4) owes 3 groups: one is lost to a damaged module. Bright Reach is
		// still in Tau Ceti. "Ghost" is a ship that no longer exists (destroyed, seized or sold).
		val carrying = fixture.with(seated(fixture.trading, KIM, "Corvid", BETA_HYDRI), KIM) { kim ->
			kim.copy(
				ships = kim.ships.map { if (it.name == "Corvid") it.copy(damagedModules = listOf(Module.PASSENGER)) else it },
				passengerGroups = listOf(
					PassengerGroup("Corvid", TAU_CETI, BETA_HYDRI, 3),
					PassengerGroup("Bright Reach", TAU_CETI, BETA_HYDRI, 2),
					PassengerGroup("Ghost", MU_HERCULIS, BETA_HYDRI, 1)
				)
			)
		}

		val opened = carrying.advancePhase(FakeDice())

		assertThat(kim(opened).money).isEqualTo(kim(carrying).money - 40)
		assertThat(kim(opened).passengerGroups).isEmpty()
		assertThat(opened.passengerGroupsLeft(tauToBeta)).isEqualTo(7)
		assertThat(opened.log.map { it.text }).contains(
			"Kessler Cargo's Corvid didn't bring 1 passenger group to Beta Hydri.",
			"Kessler Cargo's Bright Reach didn't bring 2 passenger groups to Beta Hydri.",
			"Kessler Cargo's Ghost didn't bring 1 passenger group to Beta Hydri."
		)
		// R136: a red card for Kim alone, worded with what it cost them from their own ledger.
		assertThat(opened.log.filter { it.text.contains("didn't bring") }.map { it.notice })
			.containsOnly(Notice.setback(NoticeKind.PASSENGERS_UNDELIVERED, null, listOf(KIM)))
		assertThat(kim(opened).ledger.newestFirst().first().let { it.reason to it.amount }).isEqualTo(Player.PASSENGER_PENALTY_REASON to -40)
	}

	@Test
	fun shouldChargeNothingForGroupsDeliveredToTheDestinationPort() {
		val carrying = fixture.with(seated(fixture.trading, KIM, "Corvid", BETA_HYDRI), KIM) { kim ->
			kim.copy(passengerGroups = listOf(PassengerGroup("Corvid", TAU_CETI, BETA_HYDRI, 4)))
		}

		val opened = carrying.advancePhase(FakeDice())

		assertThat(kim(opened).money).isEqualTo(kim(carrying).money)
		assertThat(kim(opened).passengerGroups).isEmpty()
		assertThat(opened.log.map { it.text }).noneMatch { it.contains("passenger") }
	}

	@Test
	fun shouldNeedReputationFifteenToBoardPassengers() {
		val low = fixture.with(opportunity, KIM) { it.copy(reputation = Reputation(14)) }

		assertThatThrownBy { low.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 1) }
			.hasMessage("Passengers travel only with a corporation of reputation 15 or more; yours is 14.")
		fixture.with(opportunity, KIM) { it.copy(reputation = Reputation(15)) }.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 1)
	}

	@Test
	fun shouldNeverEvictBookedPassengersForSlavesADeliveryOrAModuleTakenOff() {
		// Bright Reach: 2 Passenger modules (4 seats), 3 groups booked, so 1 seat is free
		val booked = kim(opportunity.takePassengers(KIM, "Bright Reach", BETA_HYDRI, 3))

		assertThatThrownBy { booked.buySpecial("Bright Reach", SpecialLot(SpecialGoods.SLAVES, 2, BETA_HYDRI), 10) }
			.hasMessage("Bright Reach has passengers booked: their seats can't be taken until they are delivered.")
		booked.buySpecial("Bright Reach", SpecialLot(SpecialGoods.SLAVES, 1, BETA_HYDRI), 10)
		assertThatThrownBy { booked.removeModule("Bright Reach", Module.PASSENGER) }
			.hasMessage("Bright Reach has passengers booked: their seats can't be taken until they are delivered.")
		assertThatThrownBy { booked.takeDelivery("Bright Reach", TipOff.O22, Module.PASSENGER, 0) }
			.hasMessage("Bright Reach has passengers booked: their seats can't be taken until they are delivered.")
		assertThat(booked.moduleMoves("Bright Reach").storable).isEmpty()
	}

	@Test
	fun shouldForgiveWhatTheCashCannotPayOfThePenalty() {
		val broke = fixture.with(fixture.trading, KIM) { kim ->
			kim.copy(money = 15, passengerGroups = listOf(PassengerGroup("Bright Reach", MU_HERCULIS, BETA_HYDRI, 2)))
		}

		val opened = broke.advancePhase(FakeDice())

		assertThat(kim(opened).money).isZero()
		assertThat(kim(opened).passengerGroups).isEmpty()
	}
}
