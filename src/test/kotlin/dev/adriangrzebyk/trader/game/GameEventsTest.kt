package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.news.ActiveTipOff
import dev.adriangrzebyk.trader.game.news.News
import dev.adriangrzebyk.trader.game.news.NewsEvent
import dev.adriangrzebyk.trader.game.news.TipOff
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R115: the creator's events, taking place when News opens. Ada's ships start in Port at Tau Ceti and Beta Hydri,
// Grace's at Gamma Leporis and Mu Herculis.
class GameEventsTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	private val initiative = Game.lobby("game-1", 5, ada).addPlayer(grace)
		.start("ada@example.com", FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE)
		.let { ConnectionsPlaced(it).byEveryone() }
		.advancePhase(FakeDice(2, 1))

	/**
	 * [tipOff] drawn so that it becomes effective as turn 1's News opens, rolling [twoD6] and [d6] for the event. An event that
	 * goes back to the pool is drawn again at once (d3 1), so everyone then finishes reading the News.
	 */
	private fun takingPlace(tipOff: TipOff, twoD6: List<Int> = emptyList(), d6: List<Int> = emptyList(), from: Game = initiative): Game {
		val opened = from.copy(news = News.NONE.copy(inPlay = listOf(ActiveTipOff(1, tipOff, 1 - tipOff.lead, emptyList(), null))))
			.advancePhase(FakeDice(twoD6, d6, listOf(1), listOf(1)))
		return if (opened.turn!!.phase == TurnPhase.NEWS) opened.signalDoneNews("ada@example.com").signalDoneNews("grace@example.com") else opened
	}

	private fun player(game: Game, email: String): Player = game.players.first { it.email == email }

	@Test
	fun shouldAnnounceTheEventAndCloseANewsWithNothingLeftInPlay() {
		val game = takingPlace(TipOff.E2)

		assertThat(game.log.map { it.text }).contains("News: ${NewsEvent.ALIEN_ATTACK.headline}")
		assertThat(game.log.first { it.text.startsWith("News: Alien raiders") }.notice?.kind).isEqualTo(NoticeKind.NEWS_EVENT)
		assertThat(game.turn!!.phase).isEqualTo(TurnPhase.JUMPS)
		assertThat(game.news.retired).containsExactly(TipOff.E2)
		assertThat(game.prices.priceOf(MarketGood(TAU_CETI, Goods.POLY))).isEqualTo(
			initiative.prices.priceOf(MarketGood(TAU_CETI, Goods.POLY)) - 3
		)
		assertThat(game.prices.prices.values).allMatch { it in 1..20 }
	}

	@Test
	fun shouldMakeEveryJumpFourHarderThisTurn() {
		// E1: Tau Ceti → Mu Herculis has a target of 9 for crew B; with −4, a roll of 6 fails
		val game = takingPlace(TipOff.E1)

		val jumped = game.jumpShip(game.turn!!.jumper()!!, player(game, game.turn!!.jumper()!!).ships.first().name, MU_HERCULIS, FakeDice(6))
		assertThat(game.news.conditions.jumpModifierIn(1)).isEqualTo(-4)
		assertThat(jumped.log.last().text).contains("failed")
		// Playtest 2026-11-21: the chance its player sees counts the news too, as the roll does (9 − 4 = 5).
		val jumper = game.turn!!.jumper()!!
		val ship = player(game, jumper).ships.first()
		assertThat(game.viewFor(jumper)!!.jumpChances.getValue(ship.name).getValue(MU_HERCULIS))
			.isEqualTo(JumpChance(ship.jumpChanceTo(MU_HERCULIS)!!.target - 4))
	}

	@Test
	fun shouldHalveCashAndLoansInTheMoneyFraud() {
		val game = takingPlace(TipOff.E4)

		assertThat(player(game, "ada@example.com").money).isEqualTo(player(initiative, "ada@example.com").money / 2)
	}

	@Test
	fun shouldPermanentlyShiftIsoSupplyDemandButNeverBelowMinusTen() {
		val game = takingPlace(TipOff.E9)

		assertThat(game.supplyDemandShifts.indicatorOf(MarketGood(MU_HERCULIS, Goods.ISO))).isEqualTo(-2)
		assertThat(game.supplyDemandShifts.indicatorOf(MarketGood(TAU_CETI, Goods.ISO))).isEqualTo(-10)
	}

	@Test
	fun shouldCloseThePortsThisTurnAndRaisePrices() {
		val game = takingPlace(TipOff.E10)
		val jumper = game.turn!!.jumper()!!

		assertThat(game.news.conditions.portsClosedIn(1)).isTrue()
		assertThat(game.news.conditions.securityChangeIn(1)).isEqualTo(1)
		assertThatThrownBy { game.jumpShip(jumper, player(game, jumper).ships.first().name, MU_HERCULIS, JUMPS_SUCCEED) }
			.isInstanceOf(InvalidJumpException::class.java)
			.hasMessageContaining("Ports are closed this turn")
	}

	@Test
	fun shouldTakeTwoD6ReputationFromEveryPlayerInTheScreening() {
		val game = takingPlace(TipOff.E11, twoD6 = listOf(4, 9))

		assertThat(player(game, "ada@example.com").reputation.value).isEqualTo(16)
		assertThat(player(game, "grace@example.com").reputation.value).isEqualTo(11)
	}

	@Test
	fun shouldLetShipsInMuHerculisPortEscapeOrBeLostToThePirates() {
		// Grace's Halcyon is in Port at Mu Herculis; a d6 of 2 loses it
		val game = takingPlace(TipOff.E12, d6 = listOf(2))

		assertThat(player(game, "grace@example.com").ships.map { it.name }).doesNotContain("Halcyon")
		assertThat(game.log.first { it.text == "Halcyon (Vega Lines) was lost to the pirates (rolled 2)" }.notice)
			.isEqualTo(Notice.setback(NoticeKind.LOST_TO_PIRATES, MU_HERCULIS, listOf("grace@example.com")))
	}

	@Test
	fun shouldLogTheLootedWarehouseInTheNewsCard() {
		// Grace keeps 5 CHIPS at Mu Herculis; the pirates take them all, and the line goes into the News card
		val stocked = initiative.copy(
			players = initiative.players.map {
				if (it.email == "grace@example.com") it.copy(warehouses = listOf(Warehouse(MU_HERCULIS, 1, mapOf(Goods.CHIPS to 5)))) else it
			}
		)
		val game = takingPlace(TipOff.E12, d6 = listOf(2), from = stocked)

		assertThat(player(game, "grace@example.com").warehouseAt(MU_HERCULIS)!!.holding(Goods.CHIPS)).isZero()
		val line = game.log.first { it.text == "Pirates at Mu Herculis: Vega Lines's warehouse lost 5 u" }
		assertThat(line.notice?.kind).isEqualTo(NoticeKind.NEWS_EVENT)
	}

	@Test
	fun shouldTaxShipsBlocksAndFactoryUnitsWhenThePlayerCanPay() {
		val game = takingPlace(TipOff.E13)

		assertThat(player(game, "ada@example.com").money).isEqualTo(player(initiative, "ada@example.com").money - 10)
		// Playtest 2026-11-21: what the event did is part of its pop-up, after its announcement.
		val announced = game.log.indexOfFirst { it.text.startsWith("News: ") && it.notice?.kind == NoticeKind.NEWS_EVENT }
		assertThat(game.log[announced + 1].text).startsWith("Special tax paid by:")
		assertThat(game.log[announced + 1].notice).isEqualTo(game.log[announced].notice)
	}

	@Test
	fun shouldStopShipBuyingThisTurnAndNextInTheGalacticWar() {
		val game = takingPlace(TipOff.E3)

		assertThat(game.news.shipsForSaleIn(2)).isFalse()
		assertThat(game.news.shipsForSaleIn(3)).isTrue()
		assertThat(game.news.conditions.policeChangeIn(1)).isEqualTo(4)
	}
}
