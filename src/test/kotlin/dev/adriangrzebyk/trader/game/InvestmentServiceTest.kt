package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E06-S01-2: Investment closes the moment the last player signals done, exactly once (A04).
class InvestmentServiceTest {

	private val gameStore = FakeGameStore()
	private val service = InvestmentService(gameStore, FakeDice())
	private val creator = Player.newcomer("creator@example.com", "Ada", "Orion Freight")
	private val joiner = Player.newcomer("joiner@example.com", "Grace", "Vega Lines")
	private val started = Game.lobby("game-1", 20, creator).addPlayer(joiner)
		.start("creator@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
			ConnectionsPlaced(it).byEveryone()
		}

	/** [started] taken through Bidding, Ship-jumps and Trading to the start of Investment: advancing a phase needs no players' acts. */
	private val investing = (1..3).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

	@Test
	fun shouldStoreTheDoneSignal() {
		gameStore.create(investing)

		val game = service.signalDone("game-1", "joiner@example.com")

		assertThat(game.turn?.doneInvesting).containsExactly("joiner@example.com")
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldStartTheNextTurnInTheStoreWhenTheLastPlayerSignalsDone() {
		gameStore.create(investing)
		service.signalDone("game-1", "joiner@example.com")

		service.signalDone("game-1", "creator@example.com")

		assertThat(gameStore.find("game-1")?.turn).isEqualTo(Turn.bidding(2))
	}

	@Test
	fun shouldEndTheTurnOnceEvenWhenTheStoreReplaysTheChange() {
		// A real store re-runs the change when another write lands first (GameStore.update); the turn must end only once.
		val oneDone = investing.signalDoneInvesting("joiner@example.com")
		gameStore.create(oneDone)

		InvestmentService(ReplayingGameStore(gameStore), FakeDice()).signalDone("game-1", "creator@example.com")

		val stored = gameStore.find("game-1")!!
		assertThat(stored.turn).isEqualTo(Turn.bidding(2))
		assertThat(stored.log).isEqualTo(oneDone.signalDoneInvesting("creator@example.com").log)
	}

	@Test
	fun shouldRefuseALateSignalAndLeaveTheGameAsItWas() {
		val closed = investing.signalDoneInvesting("joiner@example.com").signalDoneInvesting("creator@example.com")
		gameStore.create(closed)

		assertThatThrownBy { service.signalDone("game-1", "joiner@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
		assertThat(gameStore.find("game-1")).isEqualTo(closed)
	}

	@Test
	fun shouldRefuseASignalOutsideInvestment() {
		gameStore.create(started)

		assertThatThrownBy { service.signalDone("game-1", "joiner@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldRefuseASignalFromSomeoneWhoIsNotInTheGame() {
		gameStore.create(investing)

		assertThatThrownBy { service.signalDone("game-1", "stranger@example.com") }.isInstanceOf(NotAGameMemberException::class.java)
	}

	/** [investing] where the creator owns an ISO factory at Tau Ceti, where their Wayfarer is in port. */
	private val withFactory = investing.copy(
		players = investing.players.map {
			if (it.email == "creator@example.com") it.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 6))) else it
		}
	)

	@Test
	fun shouldStoreACollectInTheStore() {
		// E06-S03-5
		gameStore.create(withFactory)

		val game = service.collect("game-1", "creator@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 6)))

		assertThat(game.turn?.collected).containsExactly("creator@example.com")
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldStoreACollectOnceEvenWhenTheStoreReplaysTheChange() {
		// a real store re-runs the change when another write lands first (GameStore.update); the output must be stored once
		gameStore.create(withFactory)

		InvestmentService(ReplayingGameStore(gameStore), FakeDice())
			.collect("game-1", "creator@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, "Wayfarer", 6)))

		val ship = gameStore.find("game-1")!!.players.first { it.email == "creator@example.com" }.ship("Wayfarer")!!
		assertThat(ship.holding(Goods.ISO)).isEqualTo(6)
	}

	@Test
	fun shouldRefuseACollectAfterInvestmentClosed() {
		val closed = investing.signalDoneInvesting("joiner@example.com").signalDoneInvesting("creator@example.com")
		gameStore.create(closed)

		assertThatThrownBy { service.collect("game-1", "creator@example.com", emptyList()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldStoreBoughtBlocksInTheStore() {
		// E06-S04
		gameStore.create(investing)

		val game = service.buyWarehouseBlocks("game-1", "creator@example.com", TAU_CETI, 2)

		assertThat(game.players.first { it.email == "creator@example.com" }.warehouseAt(TAU_CETI)!!.blocks).isEqualTo(2)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldTakeTheMoneyOnceEvenWhenTheStoreReplaysTheChange() {
		// the purchase is checked against the saved balance, so a replayed change charges and builds once
		gameStore.create(investing)

		InvestmentService(ReplayingGameStore(gameStore), FakeDice()).buyWarehouseBlocks("game-1", "creator@example.com", TAU_CETI, 2)

		val ada = gameStore.find("game-1")!!.players.first { it.email == "creator@example.com" }
		assertThat(ada.warehouseAt(TAU_CETI)!!.blocks).isEqualTo(2)
		assertThat(ada.money).isEqualTo(investing.players.first { it.email == "creator@example.com" }.money - 40)
	}

	@Test
	fun shouldRefuseBlocksAfterInvestmentClosed() {
		val closed = investing.signalDoneInvesting("joiner@example.com").signalDoneInvesting("creator@example.com")
		gameStore.create(closed)

		assertThatThrownBy { service.buyWarehouseBlocks("game-1", "creator@example.com", TAU_CETI, 1) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldStoreBoughtFactoryUnitsInTheStore() {
		// E06-S05
		gameStore.create(investing)

		val game = service.buyFactoryUnits("game-1", "creator@example.com", TAU_CETI, Goods.ISO, 2)

		assertThat(game.players.first { it.email == "creator@example.com" }.factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 2))
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}

	@Test
	fun shouldBuildAndChargeOnceEvenWhenTheStoreReplaysTheChange() {
		gameStore.create(investing)
		val price = investing.prices.priceOf(MarketGood(TAU_CETI, Goods.ISO))

		InvestmentService(ReplayingGameStore(gameStore), FakeDice()).buyFactoryUnits("game-1", "creator@example.com", TAU_CETI, Goods.ISO, 2)

		val ada = gameStore.find("game-1")!!.players.first { it.email == "creator@example.com" }
		assertThat(ada.factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 2))
		assertThat(ada.money).isEqualTo(investing.players.first { it.email == "creator@example.com" }.money - 2 * (5 * price + 10))
	}

	@Test
	fun shouldRefuseFactoryUnitsAfterInvestmentClosed() {
		val closed = investing.signalDoneInvesting("joiner@example.com").signalDoneInvesting("creator@example.com")
		gameStore.create(closed)

		assertThatThrownBy { service.buyFactoryUnits("game-1", "creator@example.com", TAU_CETI, Goods.ISO, 1) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	/** Runs every change twice against the same stored state, the way an optimistic retry re-runs it. */
	private class ReplayingGameStore(private val delegate: FakeGameStore) : GameStore by delegate {

		override fun update(id: String, mutate: (Game) -> Game): Game {
			mutate(delegate.games.getValue(id))
			return delegate.update(id, mutate)
		}
	}
}
