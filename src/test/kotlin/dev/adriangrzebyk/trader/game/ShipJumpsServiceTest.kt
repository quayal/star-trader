package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.trading.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.trading.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.trading.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.trading.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ShipJumpsServiceTest {

	private val gameStore = FakeGameStore()
	private val service = ShipJumpsService(gameStore)
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")

	// Ada rolls 2, Grace 1: Ada picks first.
	private val shipJumps = Game.lobby("game-1", 20, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS))
		.advancePhase(FakeDice(2, 1))

	@Test
	fun shouldStoreThePick() {
		gameStore.create(shipJumps)

		val game = service.pickPlace("game-1", "creator@example.com", 2)

		assertThat(game.turn!!.turnOrder()).containsExactly("joiner@example.com", "creator@example.com")
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldRefuseAPickOutOfTurnAndLeaveTheGameAsItWas() {
		gameStore.create(shipJumps)

		assertThatThrownBy { service.pickPlace("game-1", "joiner@example.com", 1) }.isInstanceOf(NotYourTurnException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(shipJumps)
	}

	@Test
	fun shouldPickOnceEvenWhenTheStoreReplaysTheChange() {
		gameStore.create(shipJumps)
		val replaying = ShipJumpsService(ReplayingGameStore(gameStore))

		val game = replaying.pickPlace("game-1", "creator@example.com", 1)

		// A04: one pick, one auto-assigned place, one set of log lines, however often the change is run.
		assertThat(game.turn!!.turnOrderPicks).hasSize(2)
		assertThat(game.log.count { it.text.startsWith("Turn order (for this turn)") }).isEqualTo(1)
	}

	@Test
	fun shouldStoreTheJump() {
		gameStore.create(shipJumps.pickTurnOrderPlace("creator@example.com", 1))

		val game = service.jump("game-1", "creator@example.com", "Wayfarer", MU_HERCULIS)

		assertThat(game.players.first().ships.first().system).isEqualTo(MU_HERCULIS)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldRefuseAJumpOutOfTurnAndLeaveTheGameAsItWas() {
		val ordered = shipJumps.pickTurnOrderPlace("creator@example.com", 1)
		gameStore.create(ordered)

		assertThatThrownBy { service.jump("game-1", "joiner@example.com", "Meridian", TAU_CETI) }.isInstanceOf(NotYourTurnException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(ordered)
	}

	@Test
	fun shouldJumpOnceEvenWhenTheStoreReplaysTheChange() {
		gameStore.create(shipJumps.pickTurnOrderPlace("creator@example.com", 1))
		val replaying = ShipJumpsService(ReplayingGameStore(gameStore))

		val game = replaying.jump("game-1", "creator@example.com", "Wayfarer", MU_HERCULIS)

		// A04: one jump and one log line, however often the change is run.
		assertThat(game.turn!!.jumpedShips).containsExactly("Wayfarer")
		assertThat(game.log.count { it.text.startsWith("Jump:") }).isEqualTo(1)
	}

	@Test
	fun shouldStoreTheLanding() {
		gameStore.create(shipJumps.pickTurnOrderPlace("creator@example.com", 1).jumpShip("creator@example.com", "Wayfarer", MU_HERCULIS))

		val game = service.land("game-1", "creator@example.com", ShipLocation.PLANET)

		assertThat(game.players.first().ships.first().location).isEqualTo(ShipLocation.PLANET)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldLandOnceEvenWhenTheStoreReplaysTheChange() {
		gameStore.create(shipJumps.pickTurnOrderPlace("creator@example.com", 1).jumpShip("creator@example.com", "Wayfarer", MU_HERCULIS))
		val replaying = ShipJumpsService(ReplayingGameStore(gameStore))

		val game = replaying.land("game-1", "creator@example.com", ShipLocation.PORT)

		// A04: one landing line, however often the change is run.
		assertThat(game.log.count { it.text.startsWith("Landing:") }).isEqualTo(1)
		assertThat(game.turn!!.landingShip).isNull()
	}

	@Test
	fun shouldStoreTheEndOfASlot() {
		gameStore.create(shipJumps.pickTurnOrderPlace("creator@example.com", 1))

		val game = service.finishJumping("game-1", "creator@example.com")

		assertThat(game.turn!!.jumper()).isEqualTo("joiner@example.com")
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldFinishOnceEvenWhenTheStoreReplaysTheChange() {
		gameStore.create(shipJumps.pickTurnOrderPlace("creator@example.com", 1))
		val replaying = ShipJumpsService(ReplayingGameStore(gameStore))

		val game = replaying.finishJumping("game-1", "creator@example.com")

		assertThat(game.turn!!.doneJumping).containsExactly("creator@example.com")
	}

	@Test
	fun shouldRejectAnUnknownGame() {
		assertThatThrownBy {
			service.jump("missing", "creator@example.com", "Wayfarer", MU_HERCULIS)
		}.isInstanceOf(GameNotFoundException::class.java)
		assertThatThrownBy { service.finishJumping("missing", "creator@example.com") }.isInstanceOf(GameNotFoundException::class.java)
		assertThatThrownBy { service.land("missing", "creator@example.com", ShipLocation.PORT) }.isInstanceOf(GameNotFoundException::class.java)
	}

	@Test
	fun shouldRejectAnUnknownGameForAPick() {
		assertThatThrownBy { service.pickPlace("missing", "creator@example.com", 1) }.isInstanceOf(GameNotFoundException::class.java)
	}

	/** Runs every change twice against the same stored state, the way an optimistic retry re-runs it. */
	private class ReplayingGameStore(private val delegate: FakeGameStore) : GameStore by delegate {

		override fun update(id: String, mutate: (Game) -> Game): Game {
			mutate(delegate.games.getValue(id))
			return delegate.update(id, mutate)
		}
	}
}
