package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E04-S03: after each jump the player chooses where the ship ends up (R30), and the game waits for it (D09).
class GameLandingTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val kim = Player.newcomer("kim@example.com", "Kim", "Kessler Cargo")

	// Turn order Grace, Ada, Kim (see GameShipJumpTest); Grace's Meridian is at Gamma Leporis, Halcyon at Mu Herculis.
	private val jumping = Game.lobby("game-1", 3, ada).addPlayer(grace).addPlayer(kim)
		.start(
			"ada@example.com",
			FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to EPSILON_ERIDANI),
			START_DICE
		).let { ConnectionsPlaced(it).byEveryone() }
		.advancePhase(FakeDice(1, 1, 2, 1, 2))
		.pickTurnOrderPlace("kim@example.com", 3, NO_NEWS)
		.pickTurnOrderPlace("grace@example.com", 1, NO_NEWS)

	private val arrived = jumping.jumpShip("grace@example.com", "Meridian", TAU_CETI, JUMPS_SUCCEED)

	@Test
	fun shouldWaitForTheLandingAfterAJump() {
		assertThat(arrived.turn!!.landingShip).isEqualTo("Meridian")
		assertThat(jumping.turn!!.landingShip).isNull()
	}

	@Test
	fun shouldNotLandAShipInTheShipyard() {
		// R60: only a ship bought there is in the yard.
		assertThatThrownBy {
			arrived.landShip("grace@example.com", ShipLocation.SHIPYARD, LANDS_SAFELY)
		}.isInstanceOf(InvalidLandingException::class.java)
	}

	@Test
	fun shouldPutTheShipWhereThePlayerChooses() {
		ShipLocation.entries.filter { it.isLandable() }.forEach { location ->
			val game = arrived.landShip("grace@example.com", location, LANDS_SAFELY)

			assertThat(
				shipOf(game, "Meridian")
			).isEqualTo(
				Ship(
					"Meridian", TAU_CETI, location, emptyMap(), Ship.STARTING_HULL, Ship.STARTING_MODULES, Ship.STARTING_CREW_CLASS,
					null, 0, emptyList()
				)
			)
			assertThat(game.turn!!.landingShip).isNull()
		}
	}

	@Test
	fun shouldLogTheLanding() {
		assertThat(arrived.landShip("grace@example.com", ShipLocation.PORT, LANDS_SAFELY).log.last())
			.isEqualTo(LogEntry.event(1, "Landing: Vega Lines — Meridian lands at the port (Tau Ceti)"))
		assertThat(arrived.landShip("grace@example.com", ShipLocation.PLANET, LANDS_SAFELY).log.last())
			.isEqualTo(LogEntry.event(1, "Meridian (Vega Lines) landed on Tau Ceti's Planet: 12 vs security 7.")) // R116
		assertThat(arrived.landShip("grace@example.com", ShipLocation.SPACE, LANDS_SAFELY).log.last())
			.isEqualTo(LogEntry.event(1, "Landing: Vega Lines — Meridian stays in space (Tau Ceti)"))
	}

	@Test
	fun shouldKeepTheSlotUntilTheLastShipHasLanded() {
		val lastJumped = arrived.landShip(
			"grace@example.com",
			ShipLocation.PORT,
			LANDS_SAFELY
		).jumpShip("grace@example.com", "Halcyon", TAU_CETI, JUMPS_SUCCEED)

		// S03-6: the jump isn't finished until the landing is confirmed.
		assertThat(lastJumped.turn!!.jumper()).isEqualTo("grace@example.com")
		assertThat(lastJumped.landShip("grace@example.com", ShipLocation.SPACE, LANDS_SAFELY).turn!!.jumper()).isEqualTo("ada@example.com")
	}

	@Test
	fun shouldRefuseAnotherJumpOrFinishingWhileAShipWaitsToLand() {
		// S02-3: the next jump is made after the previous one has resolved.
		assertThatThrownBy { arrived.jumpShip("grace@example.com", "Halcyon", TAU_CETI, JUMPS_SUCCEED) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Choose where Meridian ends up first.")
		assertThatThrownBy { arrived.finishJumping("grace@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Choose where Meridian ends up first.")
	}

	@Test
	fun shouldRefuseALandingWithNoShipWaiting() {
		assertThatThrownBy { jumping.landShip("grace@example.com", ShipLocation.PORT, LANDS_SAFELY) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("No ship is waiting to land.")
	}

	@Test
	fun shouldRefuseALandingWithoutALocation() {
		assertThatThrownBy { arrived.landShip("grace@example.com", null, LANDS_SAFELY) }
			.isInstanceOf(InvalidLandingException::class.java)
			.hasMessage("Choose where the ship ends up.")
	}

	@Test
	fun shouldRefuseALandingOutOfTurnOrFromSomeoneWhoIsNotAPlayer() {
		assertThatThrownBy { arrived.landShip("ada@example.com", ShipLocation.PORT, LANDS_SAFELY) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Vega Lines's turn to jump.")
		assertThatThrownBy { arrived.landShip("eve@example.com", ShipLocation.PORT, LANDS_SAFELY) }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	private fun shipOf(game: Game, name: String): Ship = game.players.flatMap { it.ships }.first { it.name == name }
}
