package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E06-S05: building or extending a factory in Investment, after Collect (R46, R48).
class GameBuyFactoryUnitsTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	/** Ada's Wayfarer is at Tau Ceti, Grace's Meridian at Gamma Leporis (R40). */
	private val started = Game.lobby("game-1", 20, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
			ConnectionsPlaced(it).byEveryone()
		}

	private val investing = (1..5).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

	private val isoAtTau = MarketGood(TAU_CETI, Goods.ISO)

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	private fun withFactoryFor(game: Game, email: String): Game =
		game.copy(players = game.players.map { if (it.email == email) it.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 4, 0))) else it })

	@Test
	fun shouldBuildTheFactoryAtTheGamesCurrentPriceAndLogItWithoutThePrice() {
		val price = investing.prices.priceOf(isoAtTau)

		val game = investing.buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 2)

		assertThat(playerOf(game, "ada@example.com").factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 2, 0))
		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(playerOf(investing, "ada@example.com").money - 2 * (5 * price + 10))
		assertThat(game.log.last()).isEqualTo(LogEntry.event(game.turn!!.number, "Orion Freight added 2 units of ISO production at Tau Ceti"))
		assertThat(game.log.last().text).doesNotContain("HT") // R50: money is private
	}

	@Test
	fun shouldUseThePriceTradingLeftTheGoodAt() {
		// S05-2: the price is the one on the Deck, which Trading moved
		val moved = investing.copy(prices = investing.prices.withPrice(isoAtTau, 20))

		val game = moved.buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 1)

		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(playerOf(moved, "ada@example.com").money - 110)
	}

	@Test
	fun shouldLogOneUnitInTheSingular() {
		assertThat(investing.buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 1).log.last().text)
			.isEqualTo("Orion Freight added 1 unit of ISO production at Tau Ceti")
	}

	@Test
	fun shouldLeaveEveryoneElseUntouched() {
		val game = investing.buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 1)

		assertThat(playerOf(game, "grace@example.com")).isEqualTo(playerOf(investing, "grace@example.com"))
	}

	@Test
	fun shouldGrowAFactoryWithoutACollectInInvestment() {
		// R102: Collect happened in Opportunity, so the new units first produce at the next one (R48)
		val game = withFactoryFor(investing, "ada@example.com").buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 2)

		assertThat(playerOf(game, "ada@example.com").factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 6, 0))
		assertThat(playerOf(game, "ada@example.com").ship("Wayfarer")!!.holding(Goods.ISO)).isZero()
		assertThat(game.turn!!.isCollecting("ada@example.com")).isFalse()
		assertThat(game.signalDoneInvesting("ada@example.com").turn!!.doneInvesting).containsExactly("ada@example.com")
	}

	@Test
	fun shouldLetThemBuyMoreAfterTheFirstFactory() {
		val game = investing.buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 1).buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 1)

		assertThat(playerOf(game, "ada@example.com").factories).containsExactly(Factory(TAU_CETI, Goods.ISO, 2, 0))
	}

	@Test
	fun shouldLetAPlayerBuyAfterSignallingDoneBecauseDoneIsAReadinessFlag() {
		val game = investing.signalDoneInvesting("ada@example.com").buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 1)

		assertThat(playerOf(game, "ada@example.com").factories).hasSize(1)
		assertThat(game.turn!!.doneInvesting).containsExactly("ada@example.com")
	}

	@Test
	fun shouldRefuseOutsideInvestment() {
		assertThatThrownBy { started.buyFactoryUnits("ada@example.com", TAU_CETI, Goods.ISO, 1) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldRefuseAPlayerWhoIsNotInTheGame() {
		assertThatThrownBy { investing.buyFactoryUnits("eve@example.com", TAU_CETI, Goods.ISO, 1) }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldPassTheRulesOfThePlayerThrough() {
		assertThatThrownBy { investing.buyFactoryUnits("ada@example.com", TAU_CETI, Goods.CHIPS, 1) }
			.isInstanceOf(InvalidInvestmentException::class.java)
	}
}
