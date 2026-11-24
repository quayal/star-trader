package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E06-S03, R102: on their Opportunity turn a player with factories places their output before anything else (R47, R48).
class GameCollectTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	/** Ada's Wayfarer is at Tau Ceti, Grace's Meridian at Gamma Leporis (R40: two ships each, in different systems). */
	private val started = Game.lobby("game-1", 20, ada).addPlayer(grace)
		.start("ada@example.com", START_DICE).let {
			StartingShips(FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS)).placedIn(it)
		}.let {
			ConnectionsPlaced(it).byEveryone()
		}

	/** [started] taken to the start of Opportunity, Ada first in the order: advancing a phase needs no players' acts. */
	private val investing = (1..4).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

	private val isoAtTau = Factory(TAU_CETI, Goods.ISO, 6, 0)

	/** [investing] where Ada owns an ISO factory at Tau Ceti and a one-block warehouse there; Grace owns nothing. */
	private val withFactory = investing.copy(
		players = investing.players.map {
			if (it.email == "ada@example.com") it.copy(factories = listOf(isoAtTau), warehouses = listOf(Warehouse(TAU_CETI, 1, emptyMap()))) else it
		}
	)

	private val wayfarerShip = "Wayfarer"

	/** [withFactory] where Ada owns [factories] instead. */
	private fun withFactories(vararg factories: Factory): Game = withFactory.copy(
		players = withFactory.players.map { if (it.email == "ada@example.com") it.copy(factories = factories.toList()) else it }
	)

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	@Test
	fun shouldStoreTheOutputAndRecordThatTheyCollected() {
		val game = withFactory.collectOutput(
			"ada@example.com",
			listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 4), OutputPlacement(TAU_CETI, Goods.ISO, null, 2))
		)

		assertThat(playerOf(game, "ada@example.com").ship(wayfarerShip)!!.holding(Goods.ISO)).isEqualTo(4)
		assertThat(playerOf(game, "ada@example.com").warehouseAt(TAU_CETI)!!.holding(Goods.ISO)).isEqualTo(2)
		assertThat(game.turn!!.collected).containsExactly("ada@example.com")
	}

	@Test
	fun shouldLoseTheOutputWhenNothingIsPlacedButStillRecordTheCollect() {
		val game = withFactory.collectOutput("ada@example.com", emptyList())

		assertThat(playerOf(game, "ada@example.com").ship(wayfarerShip)!!.holding(Goods.ISO)).isZero()
		assertThat(game.turn!!.collected).containsExactly("ada@example.com")
	}

	@Test
	fun shouldLogWhatEachFactoryCollectedInTheTurnOfTheCollect() {
		// E06-S07-1
		val game = withFactory.collectOutput(
			"ada@example.com",
			listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 4), OutputPlacement(TAU_CETI, Goods.ISO, null, 2))
		)

		assertThat(game.log.drop(withFactory.log.size)).containsExactly(LogEntry.event(1, "Orion Freight collected 6 u of ISO at Tau Ceti"))
	}

	@Test
	fun shouldLogWhatWasLostWhenNotEverythingWasPlaced() {
		// E06-S07-1: "(m lost)"
		val game = withFactory.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 4)))

		assertThat(game.log.last().text).isEqualTo("Orion Freight collected 4 u of ISO at Tau Ceti (2 lost)")
	}

	@Test
	fun shouldCountOutputThatDidNotFitAsLost() {
		// R16: the Wayfarer has 8 cargo spaces (R55), so 2 of 10 placed units don't fit
		val big = withFactories(Factory(TAU_CETI, Goods.ISO, 10, 0))

		val game = big.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 10)))

		assertThat(game.log.last().text).isEqualTo("Orion Freight collected 8 u of ISO at Tau Ceti (2 lost)")
	}

	@Test
	fun shouldLogOneLinePerFactoryEvenWhenAllOfItWasLost() {
		val two = withFactories(isoAtTau, Factory(GAMMA_LEPORIS, Goods.POLY, 3, 0))

		val game = two.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 6)))

		assertThat(game.log.drop(two.log.size).map { it.text }).containsExactly(
			"Orion Freight collected 6 u of ISO at Tau Ceti",
			"Orion Freight collected 0 u of POLY at Gamma Leporis (3 lost)"
		)
	}

	@Test
	fun shouldNotLetAPlayerWithoutFactoriesCollect() {
		// S03-6: they have nothing to collect and go straight to the phase's actions
		val gracesTurn = withFactory.collectOutput("ada@example.com", emptyList()).finishOpportunities("ada@example.com")

		assertThatThrownBy { gracesTurn.collectOutput("grace@example.com", emptyList()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("You have no factories to collect from.")
	}

	@Test
	fun shouldRefuseACollectOutOfTurnNamingWhoseTurnItIs() {
		// R102: players act one at a time in the turn order
		val graceWithFactory = withFactory.copy(
			players = withFactory.players.map { if (it.email == "grace@example.com") it.copy(factories = listOf(isoAtTau)) else it }
		)

		assertThatThrownBy { graceWithFactory.collectOutput("grace@example.com", emptyList()) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Orion Freight's Opportunity turn.")
	}

	@Test
	fun shouldRefuseASecondCollectSoOutputIsStoredOnce() {
		val once = withFactory.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 6)))

		assertThatThrownBy { once.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 6))) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("You have already collected your output.")
		assertThat(playerOf(once, "ada@example.com").ship(wayfarerShip)!!.holding(Goods.ISO)).isEqualTo(6)
	}

	@Test
	fun shouldRefuseACollectOutsideOpportunity() {
		assertThatThrownBy { started.collectOutput("ada@example.com", emptyList()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Opportunity isn't open.")
		assertThatThrownBy { withFactory.advancePhase(FakeDice()).collectOutput("ada@example.com", emptyList()) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Opportunity isn't open.")
	}

	@Test
	fun shouldRefuseACollectFromSomeoneWhoIsNotInTheGame() {
		assertThatThrownBy { withFactory.collectOutput("stranger@example.com", emptyList()) }.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldNotLetAPlayerWithFactoriesBeDoneBeforeTheyCollect() {
		// R102: Collect comes before anything else on their turn
		assertThatThrownBy { withFactory.finishOpportunities("ada@example.com") }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Collect your output first.")
		assertThat(withFactory.turn!!.doneOpportunity).isEmpty()
	}

	@Test
	fun shouldPassTheTurnOnceTheyCollectedAndAreDone() {
		val done = withFactory.collectOutput("ada@example.com", emptyList()).finishOpportunities("ada@example.com")

		assertThat(done.turn!!.doneOpportunity).containsExactly("ada@example.com")
		assertThat(done.turn!!.opportunist()).isEqualTo("grace@example.com")
	}

	@Test
	fun shouldOpenInvestmentOnceTheLastPlayerIsDone() {
		// R102: after the last player, Investment starts
		val game = withFactory.collectOutput("ada@example.com", emptyList())
			.finishOpportunities("ada@example.com").finishOpportunities("grace@example.com")

		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.INVESTMENT)
		assertThat(game.turn!!.collected).containsExactly("ada@example.com")
	}

	@Test
	fun shouldProduceNothingWhileDisabledAndCountTheTurnsDown() {
		// R99: a sabotaged factory's next N Collects produce nothing
		val disabled = withFactories(isoAtTau.disabledFor(2))

		assertThatThrownBy { disabled.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 1))) }
			.isInstanceOf(InvalidCollectException::class.java)
			.hasMessage("Your ISO factory at Tau Ceti makes 0 units.")
		val game = disabled.collectOutput("ada@example.com", emptyList())

		assertThat(game.log.last().text).isEqualTo("Orion Freight collected nothing at Tau Ceti: its ISO factory is disabled")
		assertThat(playerOf(game, "ada@example.com").factories).containsExactly(isoAtTau.disabledFor(1))
	}

	@Test
	fun shouldCollectInTheNextOpportunityToo() {
		// E06-S03-7 / R48: the factory keeps making its output every turn; collects reset each Opportunity
		val next = (1..5).fold(
			withFactory.collectOutput("ada@example.com", emptyList()).finishOpportunities("ada@example.com").finishOpportunities("grace@example.com")
		) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

		assertThat(next.turn!!.phase).isEqualTo(TurnPhase.OPPORTUNITY)
		assertThat(next.turn!!.collected).isEmpty()
		assertThat(next.collectOutput("ada@example.com", listOf(OutputPlacement(TAU_CETI, Goods.ISO, wayfarerShip, 6))).turn!!.collected)
			.containsExactly("ada@example.com")
	}
}
