package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.ADA
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.GRACE
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import dev.adriangrzebyk.trader.game.sabotage.InvalidSabotageException
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R127: the Parking Hangar, offered when a ship lands in a Port that has one, renewed when its owner ends their slot.
class GameHangarTest {

	private val grace = "grace@example.com"
	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val graceAsPlayer = Player.newcomer(grace, "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	// As GameLandingTest: Grace jumps first; Meridian is at Gamma Leporis, Halcyon at Mu Herculis.
	private val jumping = Game.lobby("game-1", 3, ada).addPlayer(graceAsPlayer).addPlayer(kim)
		.start("ada@example.com", START_DICE).let {
			StartingShips(
				FakeStartingSystemPicker(
					TAU_CETI to BETA_HYDRI,
					GAMMA_LEPORIS to MU_HERCULIS,
					SIGMA_DRACONIS to EPSILON_ERIDANI
				)
			).placedIn(it)
		}.let { ConnectionsPlaced(it).byEveryone() }
		.advancePhase(FakeDice(1, 1, 2, 1, 2))
		.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS)
		.pickTurnOrderPlace(grace, 1, NO_NEWS)

	private val arrived = jumping.jumpShip(grace, "Meridian", TAU_CETI, JUMPS_SUCCEED)

	private fun player(game: Game) = game.players.first { it.email == grace }

	private fun ship(game: Game, name: String) = player(game).ship(name)!!

	@Test
	fun shouldAskWhetherToParkWhenAShipLandsInAPortWithAHangar() {
		val landed = arrived.landShip(grace, ShipLocation.PORT, LANDS_SAFELY)

		assertThat(landed.turn!!.hangarAsk).isEqualTo(HangarAsk(listOf("Meridian"), false))
		assertThat(landed.turn!!.landingShip).isNull()
	}

	@Test
	fun shouldNotAskWhenTheShipStaysInSpaceOrLandsOnThePlanet() {
		assertThat(arrived.landShip(grace, ShipLocation.SPACE, LANDS_SAFELY).turn!!.hangarAsk).isNull()
		assertThat(arrived.landShip(grace, ShipLocation.PLANET, LANDS_SAFELY).turn!!.hangarAsk).isNull()
	}

	@Test
	fun shouldNotAskInAPortWithoutAHangar() {
		val landed = jumping.jumpShip(grace, "Halcyon", SIGMA_DRACONIS, JUMPS_SUCCEED).landShip(grace, ShipLocation.PORT, LANDS_SAFELY)

		assertThat(landed.turn!!.hangarAsk).isNull()
	}

	@Test
	fun shouldNotAskAPlayerWhoCannotPay() {
		val poor = arrived.copy(players = arrived.players.map { if (it.email == grace) it.copy(money = 4) else it })

		assertThat(poor.landShip(grace, ShipLocation.PORT, LANDS_SAFELY).turn!!.hangarAsk).isNull()
	}

	@Test
	fun shouldParkTheShipAndTakeTheFeeOnYes() {
		val before = player(arrived).money

		val parked = arrived.landShip(grace, ShipLocation.PORT, LANDS_SAFELY).answerHangar(grace, true)

		assertThat(ship(parked, "Meridian").isParkedOn(1)).isTrue()
		assertThat(player(parked).money).isEqualTo(before - 5)
		assertThat(parked.turn!!.hangarAsk).isNull()
		assertThat(parked.log.last().text).isEqualTo("Parking: Vega Lines — Meridian is in the Parking Hangar (Tau Ceti)")
		// Halcyon hasn't jumped, so the slot goes on.
		assertThat(parked.turn!!.jumper()).isEqualTo(grace)
	}

	@Test
	fun shouldLeaveTheShipOutAndChargeNothingOnNo() {
		val landed = arrived.landShip(grace, ShipLocation.PORT, LANDS_SAFELY)

		val declined = landed.answerHangar(grace, false)

		assertThat(ship(declined, "Meridian").isInHangar()).isFalse()
		assertThat(player(declined).money).isEqualTo(player(landed).money)
		assertThat(declined.turn!!.hangarAsk).isNull()
		assertThat(declined.log).isEqualTo(landed.log)
	}

	@Test
	fun shouldEndTheSlotAfterTheAnswerWhenEveryShipHasJumped() {
		val game = jumping.jumpShip(grace, "Halcyon", SIGMA_DRACONIS, JUMPS_SUCCEED).landShip(grace, ShipLocation.SPACE, LANDS_SAFELY)
			.jumpShip(grace, "Meridian", TAU_CETI, JUMPS_SUCCEED).landShip(grace, ShipLocation.PORT, LANDS_SAFELY)

		assertThat(game.turn!!.jumper()).isEqualTo(grace)

		val answered = game.answerHangar(grace, true)

		assertThat(answered.turn!!.doneJumping).containsExactly(grace)
		assertThat(answered.turn!!.jumper()).isEqualTo("ada@example.com")
	}

	@Test
	fun shouldWaitForTheAnswerBeforeAnythingElseInTheSlot() {
		val asked = arrived.landShip(grace, ShipLocation.PORT, LANDS_SAFELY)

		assertThatThrownBy { asked.jumpShip(grace, "Halcyon", SIGMA_DRACONIS, JUMPS_SUCCEED) }
			.isInstanceOf(InvalidGameOperationException::class.java)
		assertThatThrownBy { asked.finishJumping(grace) }.isInstanceOf(InvalidGameOperationException::class.java)
	}

	@Test
	fun shouldRefuseAnAnswerWithNoQuestionOrOutOfTurn() {
		assertThatThrownBy { jumping.answerHangar(grace, true) }.isInstanceOf(InvalidGameOperationException::class.java)
		assertThatThrownBy { arrived.landShip(grace, ShipLocation.PORT, LANDS_SAFELY).answerHangar("ada@example.com", true) }
			.isInstanceOf(NotYourTurnException::class.java)
	}

	/** [jumping] with Grace's Meridian already in the hangar at Gamma Leporis since turn 0, so it's due for renewal in turn 1. */
	private fun withStaleParking(game: Game = jumping): Game = game.copy(
		players = game.players.map {
			if (it.email ==
				grace
			) {
				it.copy(ships = it.ships.map { ship -> if (ship.name == "Meridian") ship.copy(hangarPaidOnTurn = 0) else ship })
			} else {
				it
			}
		}
	)

	@Test
	fun shouldAskWhetherAShipParkedLastTurnStaysWhenItsOwnerFinishesJumping() {
		val finishing = withStaleParking().finishJumping(grace)

		assertThat(finishing.turn!!.hangarAsk).isEqualTo(HangarAsk(listOf("Meridian"), true))
		assertThat(finishing.turn!!.jumper()).isEqualTo(grace)
		assertThat(finishing.turn!!.doneJumping).isEmpty()
	}

	@Test
	fun shouldKeepTheShipInTheHangarForAnotherTurnAndEndTheSlotOnYes() {
		val finishing = withStaleParking().finishJumping(grace)

		val stayed = finishing.answerHangar(grace, true)

		assertThat(ship(stayed, "Meridian").isParkedOn(1)).isTrue()
		assertThat(player(stayed).money).isEqualTo(player(finishing).money - 5)
		assertThat(stayed.turn!!.doneJumping).containsExactly(grace)
		assertThat(stayed.turn!!.jumper()).isEqualTo("ada@example.com")
	}

	@Test
	fun shouldTakeTheShipOutOfTheHangarAndEndTheSlotOnNo() {
		val finishing = withStaleParking().finishJumping(grace)

		val left = finishing.answerHangar(grace, false)

		assertThat(ship(left, "Meridian").isInHangar()).isFalse()
		assertThat(player(left).money).isEqualTo(player(finishing).money)
		assertThat(left.log.last().text).isEqualTo("Parking: Vega Lines — Meridian leaves the Parking Hangar (Gamma Leporis)")
		assertThat(left.turn!!.jumper()).isEqualTo("ada@example.com")
	}

	@Test
	fun shouldRefuseToKeepAShipWithoutTheFee() {
		val poor = withStaleParking().let { game ->
			game.copy(players = game.players.map { if (it.email == grace) it.copy(money = 4) else it })
		}.finishJumping(grace)

		assertThatThrownBy { poor.answerHangar(grace, true) }.isInstanceOf(InvalidHangarException::class.java)
		assertThat(poor.answerHangar(grace, false).turn!!.jumper()).isEqualTo("ada@example.com")
	}

	@Test
	fun shouldNotAskAShipThatJumpedAway() {
		val game = withStaleParking().jumpShip(grace, "Meridian", TAU_CETI, JUMPS_SUCCEED)

		assertThat(ship(game, "Meridian").isInHangar()).isFalse()
		assertThat(game.landShip(grace, ShipLocation.SPACE, LANDS_SAFELY).finishJumping(grace).turn!!.hangarAsk).isNull()
	}

	@Test
	fun shouldAskAboutAParkedShipWhoseJumpFailedWhenTheSlotEndsByItself() {
		val failing = FakeDice.everyTwoD6(12)

		val game = withStaleParking().jumpShip(grace, "Meridian", MU_HERCULIS, failing).jumpShip(grace, "Halcyon", GAMMA_LEPORIS, failing)

		assertThat(game.turn!!.hangarAsk).isEqualTo(HangarAsk(listOf("Meridian"), true))
		assertThat(game.turn!!.doneJumping).isEmpty()
	}

	@Test
	fun shouldAskEachShipInTurn() {
		val both = withStaleParking().let { game ->
			game.copy(
				players = game.players.map {
					if (it.email ==
						grace
					) {
						it.copy(ships = it.ships.map { ship -> ship.copy(location = ShipLocation.PORT, system = TAU_CETI, hangarPaidOnTurn = 0) })
					} else {
						it
					}
				}
			)
		}.finishJumping(grace)

		assertThat(both.turn!!.hangarAsk!!.ships).containsExactly("Meridian", "Halcyon")

		val next = both.answerHangar(grace, true)

		assertThat(next.turn!!.hangarAsk).isEqualTo(HangarAsk(listOf("Halcyon"), true))
		assertThat(next.turn!!.jumper()).isEqualTo(grace)
		assertThat(next.answerHangar(grace, false).turn!!.jumper()).isEqualTo("ada@example.com")
	}

	// Sabotage: the Opportunity phase's fixture; Kim sabotages Grace's Halcyon, in Port at Epsilon Eridani, which has a hangar.
	private val fixture = OpportunityGame()
	private val halcyon = SabotageTarget.Ship(GRACE, EPSILON_ERIDANI, "Halcyon")

	private fun withHalcyonParked(turn: Int): Game = fixture.with(fixture.opportunity, GRACE) {
		it.copy(ships = it.ships.map { ship -> if (ship.name == "Halcyon") ship.copy(hangarPaidOnTurn = turn) else ship })
	}

	@Test
	fun shouldRefuseSabotageOnAShipParkedThisTurnWithoutTakingTheFee() {
		val game = withHalcyonParked(1)

		assertThatThrownBy { game.sabotage(KIM, halcyon, ADA, FakeDice(12, 12)) }.isInstanceOf(InvalidSabotageException::class.java)
		assertThat(fixture.playerOf(game, KIM).money).isEqualTo(fixture.playerOf(fixture.opportunity, KIM).money)
	}

	@Test
	fun shouldSabotageAShipWhoseParkingWasPaidForAnEarlierTurn() {
		val game = withHalcyonParked(0).sabotage(KIM, halcyon, ADA, FakeDice(12, 12))

		assertThat(fixture.playerOf(game, KIM).money).isLessThan(fixture.playerOf(fixture.opportunity, KIM).money)
	}

	@Test
	fun shouldStillSabotageAShipInAPortWithoutAHangar() {
		val meridian = SabotageTarget.Ship(GRACE, SIGMA_DRACONIS, "Meridian")

		assertThat(fixture.opportunity.sabotage(KIM, meridian, ADA, FakeDice(12, 12)).log).hasSizeGreaterThan(fixture.opportunity.log.size)
		assertThat(MU_HERCULIS.hasHangar()).isFalse()
	}
}
