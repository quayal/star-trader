package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R58, E08-S02: a player moves goods between a ship in port and their warehouse there, in any phase.
class GameMoveCargoTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val lobby = Game.lobby("game-1", 20, ada).addPlayer(grace)

	/** Ada's Wayfarer starts in port at Tau Ceti (R40); she has a one-block warehouse there holding 6 ISO. */
	private val bidding = withWarehouseFor(
		lobby.start("ada@example.com", START_DICE).let {
			StartingShips(
				FakeStartingSystemPicker(
					TAU_CETI to BETA_HYDRI,
					GAMMA_LEPORIS to MU_HERCULIS
				)
			).placedIn(it)
		}.let {
			ConnectionsPlaced(it).byEveryone()
		}
	)

	private fun withWarehouseFor(game: Game): Game = game.copy(
		players = game.players.map {
			if (it.email == "ada@example.com") it.copy(warehouses = listOf(Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 6)))) else it
		}
	)

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	@Test
	fun shouldMoveGoodsInEveryPhaseOfARunningGame() {
		// "at any moment" (advancing a phase needs no players' acts); a News with no tip-offs closes at once (R109)
		var game = bidding
		// Control closes the turn at once when nobody is under reputation control, so a game is never left in it here (R128).
		for (phase in TurnPhase.entries - TurnPhase.NEWS - TurnPhase.CONTROL) {
			assertThat(game.turn!!.phase).isEqualTo(phase)

			val moved = game.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 1)

			assertThat(playerOf(moved, "ada@example.com").ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(1)
			assertThat(playerOf(moved, "ada@example.com").warehouseAt(TAU_CETI)!!.holding(Goods.ISO)).isEqualTo(5)
			if (phase != TurnPhase.INVESTMENT) {
				game = game.advancePhase(FakeDice(2, 1))
			}
		}
	}

	@Test
	fun shouldChangeNothingButThePlayersOwnCargo() {
		// R29: private, so no log line; the turn and everyone else stay as they were
		val moved = bidding.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 4)

		assertThat(moved.log).isEqualTo(bidding.log)
		assertThat(moved.turn).isEqualTo(bidding.turn)
		assertThat(moved.prices).isEqualTo(bidding.prices)
		assertThat(playerOf(moved, "grace@example.com")).isEqualTo(playerOf(bidding, "grace@example.com"))
		assertThat(playerOf(moved, "ada@example.com").money).isEqualTo(playerOf(bidding, "ada@example.com").money)
	}

	@Test
	fun shouldLetGoodsMadeOneTurnBeCarriedAwayTheNext() {
		// the creator's case: loaded from the warehouse during the jump slot, the ship then jumps with it
		val jumping = bidding.signalDoneBidding("ada@example.com", FakeDice(2, 1)).signalDoneBidding("grace@example.com", FakeDice(2, 1))
		val ordered = jumping.pickTurnOrderPlace(jumping.turn!!.picker()!!, 1, NO_NEWS)
		val adasSlot = if (ordered.turn!!.jumper() == "ada@example.com") ordered else ordered.finishJumping("grace@example.com")

		val away = adasSlot.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 6)
			.jumpShip("ada@example.com", "Wayfarer", MU_HERCULIS, JUMPS_SUCCEED)

		val wayfarer = playerOf(away, "ada@example.com").ship("Wayfarer")!!
		assertThat(wayfarer.system).isEqualTo(MU_HERCULIS)
		assertThat(wayfarer.holding(Goods.ISO)).isEqualTo(6)
	}

	@Test
	fun shouldNotMoveGoodsOntoAShipWaitingToLand() {
		// a ship that just jumped is in Space until its player lands it (R30)
		val jumping = bidding.signalDoneBidding("ada@example.com", FakeDice(2, 1)).signalDoneBidding("grace@example.com", FakeDice(2, 1))
		val ordered = jumping.pickTurnOrderPlace(jumping.turn!!.picker()!!, 1, NO_NEWS)
		val adasSlot = if (ordered.turn!!.jumper() == "ada@example.com") ordered else ordered.finishJumping("grace@example.com")
		val jumped = adasSlot.jumpShip("ada@example.com", "Wayfarer", MU_HERCULIS, JUMPS_SUCCEED)

		assertThatThrownBy { jumped.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
	}

	@Test
	fun shouldPassTheRulesOfThePlayerThrough() {
		assertThatThrownBy { bidding.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 7) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Your warehouse at Tau Ceti has only 6 ISO.")
	}

	@Test
	fun shouldNotLetAPlayerMoveAnotherPlayersShip() {
		// Meridian is Grace's; Ada can't name it
		assertThatThrownBy { bidding.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Meridian"), Goods.ISO, 1) }
			.isInstanceOf(InvalidCargoMoveException::class.java)
			.hasMessage("Choose one of your ships.")
	}

	@Test
	fun shouldRefuseAPlayerWhoIsNotInTheGame() {
		assertThatThrownBy { bidding.moveCargo("eve@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 1) }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldRefuseBeforeTheGameStarts() {
		assertThatThrownBy { lobby.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 1) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game hasn't started")
	}

	@Test
	fun shouldRefuseOnceTheGameIsOver() {
		val over = bidding.copy(status = GameStatus.FINISHED)

		assertThatThrownBy { over.moveCargo("ada@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 1) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game is over")
	}
}
