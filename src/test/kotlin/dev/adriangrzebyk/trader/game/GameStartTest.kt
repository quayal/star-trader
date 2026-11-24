package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R40, R41, R82, R132: a game starts with 300 HT and connection points each; the ship is named and placed in turn 1's Bidding.
class GameStartTest {

	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val lobby = Game.lobby("game-1", 20, creator).addPlayer(joiner)
	private val started = lobby.start("creator@example.com", START_DICE)

	@Test
	fun shouldGiveEveryPlayer300HtAndNoShipYet() {
		assertThat(started.players.map { it.money }).containsOnly(300)
		assertThat(started.players.flatMap { it.ships }).isEmpty()
		assertThat(started.players.flatMap { it.marketPositions }).isEmpty()
	}

	@Test
	fun shouldMoveTheGameToTurnOneBidding() {
		assertThat(started.status).isEqualTo(GameStatus.RUNNING)
		assertThat(started.turn).isEqualTo(Turn.bidding(1))
	}

	@Test
	fun shouldLogTheStartAndTheConnectionRollsInTurnOne() {
		assertThat(started.log).containsExactly(
			LogEntry.event(1, "Game started: 2 players, 20 turns"),
			LogEntry.event(1, "Orion Freight rolled 3: 6 connection points to place"),
			LogEntry.event(1, "Vega Lines rolled 3: 6 connection points to place")
		)
	}

	@Test
	fun shouldGiveEachPlayerThreePlusADThreeConnectionPointsAndNoConnectionsYet() {
		// R82, E12-S01-1: one d3 per player, in join order
		val rolled = lobby.start("creator@example.com", FakeDice.d3(1, 3))

		assertThat(rolled.players.map { it.connectionPoints }).containsExactly(4, 6)
		assertThat(rolled.players.map { it.connections }).containsOnly(Connections.NONE)
		assertThat(rolled.log.map { it.text }).contains("Orion Freight rolled 1: 4 connection points to place")
	}

	@Test
	fun shouldPlaceTheChosenNameInTheChosenPortAsAClarinetWithThreeCargoLightWeaponsPassengerAndSafeJump() {
		// R132: 6 cargo spaces, crew B paid, nothing aboard
		val placed = started.placeStartingShip("creator@example.com", "  Nimbus ", MU_HERCULIS)

		val ship = placed.players[0].ships.single()
		assertThat(ship).isEqualTo(Ship.emptyAt("Nimbus", MU_HERCULIS, ShipLocation.PORT))
		assertThat(ship.hull).isEqualTo(Hull.CLARINET)
		assertThat(ship.modules).containsExactlyInAnyOrder(
			Module.CARGO,
			Module.CARGO,
			Module.CARGO,
			Module.LIGHT_WEAPONS,
			Module.PASSENGER,
			Module.SAFE_JUMP
		)
		assertThat(ship.cargoSpaces()).isEqualTo(6)
		assertThat(ship.crewClass).isEqualTo(CrewClass.B)
		assertThat(ship.cargo).isEmpty()
		assertThat(placed.players[1].ships).isEmpty()
		assertThat(placed.players[0].money).isEqualTo(300) // the ship is free
		assertThat(placed.log.last()).isEqualTo(LogEntry.event(1, "Orion Freight starts with Nimbus at Mu Herculis"))
	}

	@Test
	fun shouldRefuseASecondShipANameInUseAndABadNameOrSystem() {
		val placed = started.placeStartingShip("creator@example.com", "Nimbus", TAU_CETI)

		assertThatThrownBy { placed.placeStartingShip("creator@example.com", "Other", BETA_HYDRI) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("You've already placed your ship.")
		assertThatThrownBy { placed.placeStartingShip("joiner@example.com", "nimbus", BETA_HYDRI) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("There's already a ship called nimbus in this game.")
		assertThatThrownBy { started.placeStartingShip("creator@example.com", " ", TAU_CETI) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("Choose a name of 1 to 20 characters.")
		assertThatThrownBy { started.placeStartingShip("creator@example.com", "A/B", TAU_CETI) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage(Ship.NAME_CHARACTERS_MESSAGE)
		assertThatThrownBy { started.placeStartingShip("creator@example.com", "Nimbus", null) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("Choose the system whose Port your ship starts in.")
	}

	@Test
	fun shouldRefuseAPlayerOutsideTheGameOrBeforeItStarts() {
		assertThatThrownBy { started.placeStartingShip("stranger@example.com", "Nimbus", TAU_CETI) }
			.isInstanceOf(NotAGameMemberException::class.java)
		assertThatThrownBy { lobby.placeStartingShip("creator@example.com", "Nimbus", TAU_CETI) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("This game hasn't started")
	}

	@Test
	fun shouldHoldTheDoneSignalOfTurnOneUntilTheShipIsPlaced() {
		val connected = ConnectionsPlaced(started).byEveryone()

		assertThatThrownBy { connected.signalDoneBidding("creator@example.com", NO_NEWS) }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("Choose your ship's name and Port first.")
		val placed = connected.placeStartingShip("creator@example.com", "Nimbus", TAU_CETI)
		assertThat(placed.signalDoneBidding("creator@example.com", NO_NEWS).turn!!.doneBidding).containsExactly("creator@example.com")
	}

	@Test
	fun shouldLetOnlyTheCreatorStart() {
		assertThatThrownBy { lobby.start("joiner@example.com", START_DICE) }
			.isInstanceOf(NotTheCreatorException::class.java)
			.hasMessage("Only the creator can start the game")
	}

	@Test
	fun shouldNotStartWithFewerThanTwoPlayers() {
		assertThatThrownBy { Game.lobby("game-1", 20, creator).start("creator@example.com", START_DICE) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("At least 2 players are needed to start")
	}

	@Test
	fun shouldNotStartTwice() {
		assertThatThrownBy { started.start("creator@example.com", START_DICE) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game has already started")
	}

	@Test
	fun shouldCloseJoiningOnceStarted() {
		assertThatThrownBy { started.addPlayer(Player.newcomer("late@example.com", "Late", "Late Corp")) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game has already started")
	}
}
