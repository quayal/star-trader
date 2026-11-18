package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R58, E08-S02-5: a cargo move is one atomic change of the stored game (A04).
class CargoServiceTest {

	private val gameStore = FakeGameStore()
	private val service = CargoService(gameStore)
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")

	/** The creator's Wayfarer is in port at Tau Ceti, where they have a one-block warehouse holding 6 ISO. */
	private val started = Game.lobby("game-1", 20, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
			ConnectionsPlaced(it).byEveryone()
		}
		.let { game ->
			game.copy(
				players = game.players.map {
					if (it.email == "creator@example.com") it.copy(warehouses = listOf(Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 6)))) else it
				}
			)
		}

	private fun creatorIn(game: Game): Player = game.players.first { it.email == "creator@example.com" }

	@Test
	fun shouldStoreTheMove() {
		gameStore.create(started)

		val game = service.move("game-1", "creator@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 4)

		assertThat(creatorIn(game).ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(4)
		assertThat(creatorIn(game).warehouseAt(TAU_CETI)!!.holding(Goods.ISO)).isEqualTo(2)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldMoveOnceEvenWhenTheStoreReplaysTheChange() {
		// A real store re-runs the change when another write lands first (GameStore.update); goods must not double.
		gameStore.create(started)

		CargoService(
			ReplayingGameStore(gameStore)
		).move("game-1", "creator@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 4)

		val stored = creatorIn(gameStore.find("game-1")!!)
		assertThat(stored.ship("Wayfarer")!!.holding(Goods.ISO)).isEqualTo(4)
		assertThat(stored.holdingAt(TAU_CETI, Goods.ISO)).isEqualTo(6)
	}

	@Test
	fun shouldLeaveTheGameAsItWasWhenTheMoveIsRefused() {
		gameStore.create(started)

		assertThatThrownBy { service.move("game-1", "creator@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 7) }
			.isInstanceOf(InvalidCargoMoveException::class.java)

		assertThat(gameStore.find("game-1")).isEqualTo(started)
	}

	@Test
	fun shouldRefuseAGameThatDoesNotExist() {
		assertThatThrownBy { service.move("nope", "creator@example.com", CargoHolder.Warehouse, CargoHolder.Ship("Wayfarer"), Goods.ISO, 1) }
			.isInstanceOf(GameNotFoundException::class.java)
	}

	/** Runs every change twice against the same stored state, the way an optimistic retry re-runs it. */
	private class ReplayingGameStore(private val delegate: FakeGameStore) : GameStore by delegate {

		override fun update(id: String, mutate: (Game) -> Game): Game {
			mutate(delegate.games.getValue(id))
			return delegate.update(id, mutate)
		}
	}
}
