package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// E06-S04: buying warehouse blocks in Investment, after Collect (R45, R48).
class GameBuyWarehouseBlocksTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	/** Ada's Wayfarer is at Tau Ceti, Grace's Meridian at Gamma Leporis (R40). */
	private val started = Game.lobby("game-1", 20, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE).let {
			ConnectionsPlaced(it).byEveryone()
		}

	private val investing = (1..5).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }

	private fun playerOf(game: Game, email: String): Player = game.players.first { it.email == email }

	private fun withFactoryFor(game: Game, email: String): Game =
		game.copy(players = game.players.map { if (it.email == email) it.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 6, 0))) else it })

	@Test
	fun shouldBuyBlocksTakeTheMoneyAndLogItPublicly() {
		val game = investing.buyWarehouseBlocks("ada@example.com", TAU_CETI, 2)

		assertThat(playerOf(game, "ada@example.com").warehouseAt(TAU_CETI)!!.blocks).isEqualTo(2)
		assertThat(playerOf(game, "ada@example.com").money).isEqualTo(playerOf(investing, "ada@example.com").money - 40)
		assertThat(game.log.last()).isEqualTo(LogEntry.event(game.turn!!.number, "Orion Freight added 2 warehouse blocks at Tau Ceti"))
	}

	@Test
	fun shouldLogOneBlockInTheSingularWithoutAnyPrice() {
		val game = investing.buyWarehouseBlocks("ada@example.com", TAU_CETI, 1)

		assertThat(game.log.last().text).isEqualTo("Orion Freight added 1 warehouse block at Tau Ceti")
		assertThat(game.log.last().text).doesNotContain("HT") // R50: money is private
	}

	@Test
	fun shouldLeaveEveryoneElseUntouched() {
		val game = investing.buyWarehouseBlocks("ada@example.com", TAU_CETI, 1)

		assertThat(playerOf(game, "grace@example.com")).isEqualTo(playerOf(investing, "grace@example.com"))
	}

	@Test
	fun shouldLetAPlayerWithFactoriesBuyWithoutACollectInInvestment() {
		// R102: Collect happened in Opportunity, so a warehouse bought now holds nothing from it (R48)
		val game = withFactoryFor(investing, "ada@example.com").buyWarehouseBlocks("ada@example.com", TAU_CETI, 1)

		assertThat(playerOf(game, "ada@example.com").warehouseAt(TAU_CETI)!!.blocks).isEqualTo(1)
		assertThat(playerOf(game, "ada@example.com").warehouseAt(TAU_CETI)!!.cargo).isEmpty()
	}

	@Test
	fun shouldLetAPlayerBuyAfterSignallingDoneBecauseDoneIsAReadinessFlag() {
		// D09: Investment closes when everyone is done, so a player who is done may still invest until then
		val done = investing.signalDoneInvesting("ada@example.com")
		val game = done.buyWarehouseBlocks("ada@example.com", TAU_CETI, 1)

		assertThat(playerOf(game, "ada@example.com").warehouseAt(TAU_CETI)).isNotNull()
		assertThat(game.turn!!.doneInvesting).containsExactly("ada@example.com")
	}

	@Test
	fun shouldRefuseOutsideInvestment() {
		assertThatThrownBy { started.buyWarehouseBlocks("ada@example.com", TAU_CETI, 1) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("Investment isn't open.")
	}

	@Test
	fun shouldRefuseAPlayerWhoIsNotInTheGame() {
		assertThatThrownBy { investing.buyWarehouseBlocks("eve@example.com", TAU_CETI, 1) }
			.isInstanceOf(NotAGameMemberException::class.java)
	}

	@Test
	fun shouldPassTheRulesOfThePlayerThrough() {
		assertThatThrownBy { investing.buyWarehouseBlocks("ada@example.com", GAMMA_LEPORIS, 0) }
			.isInstanceOf(InvalidInvestmentException::class.java)
	}
}
