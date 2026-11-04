package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E04-S02: each player in turn order jumps their ships, one at a time, each at most once per turn (R19, R26).
class GameShipJumpTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	// Ships: Ada's Wayfarer at Tau Ceti and Lantern at Beta Hydri; Grace's Meridian at Gamma Leporis and Halcyon at
	// Mu Herculis; Kim's Bright Reach at Sigma Draconis and Corvid at Epsilon Eridani.
	private val started = Game.lobby("game-1", 3, ada).addPlayer(grace).addPlayer(kim)
		.start(
			"ada@example.com",
			FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to EPSILON_ERIDANI)
		)

	// Initiative Kim, Grace, Ada (see GameTurnOrderTest); Kim takes 3rd, Grace 1st, Ada gets 2nd.
	private val picking = started.advancePhase(FakeDice(1, 1, 2, 1, 2))
	private val jumping = picking.pickTurnOrderPlace("kim@example.com", 3).pickTurnOrderPlace("grace@example.com", 1)

	@Test
	fun shouldGiveTheFirstSlotToTheFirstPlaceOnceTheOrderIsSet() {
		assertThat(jumping.turn!!.jumper()).isEqualTo("grace@example.com")
	}

	@Test
	fun shouldHaveNobodyJumpingWhilePlayersStillPick() {
		assertThat(picking.turn!!.jumper()).isNull()
		assertThat(started.turn!!.jumper()).isNull()
	}

	@Test
	fun shouldMoveTheShipToItsDestinationAtOnceAndInSpace() {
		val game = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI)

		// R19: instant; it has left its port, and S03 asks where it ends up.
		assertThat(shipOf(game, "Meridian")).isEqualTo(Ship("Meridian", TAU_CETI, ShipLocation.SPACE, emptyMap()))
		assertThat(game.turn!!.jumpedShips).containsExactly("Meridian")
	}

	@Test
	fun shouldLogEachJump() {
		val game = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI)

		assertThat(game.log.last()).isEqualTo(LogEntry(1, "Jump: Vega Lines — Meridian → Tau Ceti"))
	}

	@Test
	fun shouldKeepTheSlotWhileThePlayerHasShipsLeftToJump() {
		val game = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI)

		assertThat(game.turn!!.jumper()).isEqualTo("grace@example.com")
		assertThat(game.turn!!.doneJumping).isEmpty()
	}

	@Test
	fun shouldPassTheSlotOnWhenThePlayerJumpsTheirLastShip() {
		val game = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI)
			.landShip("grace@example.com", ShipLocation.PORT)
			.jumpShip("grace@example.com", "Halcyon", TAU_CETI)
			.landShip("grace@example.com", ShipLocation.SPACE)

		assertThat(game.turn!!.doneJumping).containsExactly("grace@example.com")
		assertThat(game.turn!!.jumper()).isEqualTo("ada@example.com")
	}

	@Test
	fun shouldPassTheSlotOnWhenThePlayerFinishesJumpingAndLeaveTheOtherShipsWhereTheyAre() {
		val game = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI)
			.landShip("grace@example.com", ShipLocation.PORT)
			.finishJumping("grace@example.com")

		assertThat(game.turn!!.jumper()).isEqualTo("ada@example.com")
		assertThat(shipOf(game, "Halcyon")).isEqualTo(shipOf(jumping, "Halcyon"))
	}

	@Test
	fun shouldLetAPlayerFinishWithoutJumpingAtAll() {
		val game = jumping.finishJumping("grace@example.com")

		assertThat(game.turn!!.jumper()).isEqualTo("ada@example.com")
		assertThat(game.players).isEqualTo(jumping.players)
	}

	@Test
	fun shouldMoveToTradingAfterTheLastSlot() {
		val game = jumping.finishJumping("grace@example.com").finishJumping("ada@example.com")
			.jumpShip("kim@example.com", "Corvid", TAU_CETI).landShip("kim@example.com", ShipLocation.PORT).finishJumping("kim@example.com")

		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.TRADING)
		assertThat(game.turn!!.jumper()).isNull()
	}

	@Test
	fun shouldMoveToTradingWhenTheLastPlayerJumpsTheirLastShip() {
		val game = jumping.finishJumping("grace@example.com").finishJumping("ada@example.com")
			.jumpShip("kim@example.com", "Corvid", TAU_CETI)
			.landShip("kim@example.com", ShipLocation.PORT)
			.jumpShip("kim@example.com", "Bright Reach", TAU_CETI)
			.landShip("kim@example.com", ShipLocation.PLANET)

		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.TRADING)
	}

	@Test
	fun shouldStartTheNextTurnWithNoShipJumpedAndNobodyDone() {
		val trading = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI)
			.landShip("grace@example.com", ShipLocation.PORT)
			.finishJumping("grace@example.com")
			.finishJumping("ada@example.com").finishJumping("kim@example.com")

		val next = trading.advancePhase(FakeDice()).turn!!

		assertThat(next.jumpedShips).isEmpty()
		assertThat(next.doneJumping).isEmpty()
	}

	@Test
	fun shouldRefuseAJumpOutOfTurn() {
		assertThatThrownBy { jumping.jumpShip("ada@example.com", "Wayfarer", MU_HERCULIS) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Vega Lines's turn to jump.")
	}

	@Test
	fun shouldRefuseFinishingOutOfTurn() {
		assertThatThrownBy { jumping.finishJumping("kim@example.com") }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Vega Lines's turn to jump.")
	}

	@Test
	fun shouldRefuseASecondJumpOfTheSameShip() {
		val game = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI).landShip("grace@example.com", ShipLocation.PORT)

		assertThatThrownBy { game.jumpShip("grace@example.com", "Meridian", BETA_HYDRI) }
			.isInstanceOf(InvalidJumpException::class.java)
			.hasMessage("Choose one of your ships that hasn't jumped this turn.")
	}

	@Test
	fun shouldRefuseAnotherPlayersShipOrAnUnknownOne() {
		assertThatThrownBy { jumping.jumpShip("grace@example.com", "Wayfarer", MU_HERCULIS) }
			.isInstanceOf(InvalidJumpException::class.java)
			.hasMessage("Choose one of your ships that hasn't jumped this turn.")
		assertThatThrownBy { jumping.jumpShip("grace@example.com", null, MU_HERCULIS) }
			.isInstanceOf(InvalidJumpException::class.java)
			.hasMessage("Choose one of your ships that hasn't jumped this turn.")
	}

	@Test
	fun shouldRefuseAJumpWithoutADestination() {
		assertThatThrownBy { jumping.jumpShip("grace@example.com", "Meridian", null) }
			.isInstanceOf(InvalidJumpException::class.java)
			.hasMessage("Choose a destination system.")
	}

	@Test
	fun shouldRefuseAJumpToTheSystemTheShipIsAlreadyAt() {
		assertThatThrownBy { jumping.jumpShip("grace@example.com", "Meridian", GAMMA_LEPORIS) }
			.isInstanceOf(InvalidJumpException::class.java)
			.hasMessage("Meridian is already at Gamma Leporis. Pick another destination.")
	}

	@Test
	fun shouldRefuseJumpsWhilePlayersStillPick() {
		assertThatThrownBy { picking.jumpShip("kim@example.com", "Corvid", TAU_CETI) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("The turn order isn't set yet.")
		assertThatThrownBy { picking.finishJumping("kim@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("The turn order isn't set yet.")
	}

	@Test
	fun shouldRefuseJumpsDuringBiddingAndAfterShipJumps() {
		val trading = jumping.finishJumping("grace@example.com").finishJumping("ada@example.com").finishJumping("kim@example.com")

		assertThatThrownBy { started.jumpShip("ada@example.com", "Wayfarer", MU_HERCULIS) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Ship-jumps hasn't started.")
		assertThatThrownBy { trading.finishJumping("kim@example.com") }
			.isInstanceOf(PhaseClosedException::class.java)
			.hasMessage("Ship-jumps has closed")
	}

	@Test
	fun shouldRefuseJumpsInAGameSavedInShipJumpsBeforeInitiativeExisted() {
		// E03-S05 added the initiative order; a game saved in Ship-jumps before then has nobody to pick or jump.
		val older = started.copy(turn = started.turn!!.copy(phase = TurnPhase.SHIP_JUMPS))

		assertThatThrownBy { older.finishJumping("ada@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Nobody is jumping now.")
	}

	@Test
	fun shouldRefuseSomeoneWhoIsNotAPlayer() {
		assertThatThrownBy { jumping.jumpShip("eve@example.com", "Meridian", TAU_CETI) }
			.isInstanceOf(NotAGameMemberException::class.java)
		assertThatThrownBy { jumping.finishJumping("eve@example.com") }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	private fun shipOf(game: Game, name: String): Ship = game.players.flatMap { it.ships }.first { it.name == name }
}
