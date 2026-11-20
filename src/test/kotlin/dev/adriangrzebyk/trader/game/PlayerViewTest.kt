package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.battle.Battle
import dev.adriangrzebyk.trader.game.battle.BattleInterception
import dev.adriangrzebyk.trader.game.battle.BattleReport
import dev.adriangrzebyk.trader.game.battle.BattleShip
import dev.adriangrzebyk.trader.game.battle.BattleStage
import dev.adriangrzebyk.trader.game.battle.Interception
import dev.adriangrzebyk.trader.game.battle.PendingHits
import dev.adriangrzebyk.trader.game.battle.SalvoOutcome
import dev.adriangrzebyk.trader.game.battle.SalvoResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R17, R29, A05, standards §10: what one member may see of a game beyond its public state, and that another member of
// the same game doesn't get it.
class PlayerViewTest {

	private val ada = "ada@example.com"
	private val grace = "grace@example.com"
	private val kim = "kim@example.com"

	private val started = Game.lobby("game-1", 20, Player.newcomer(ada, "Ada", "Orion Freight"))
		.addPlayer(Player.newcomer(grace, "Grace", "Vega Lines"))
		.addPlayer(Player.newcomer(kim, "Kim", "Kessler Cargo"))
		.start(ada, FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS, SIGMA_DRACONIS to EPSILON_ERIDANI), START_DICE)
		.let { ConnectionsPlaced(it).byEveryone() }

	private fun withTurn(change: (Turn) -> Turn): Game = started.copy(turn = change(started.turn!!))

	@Test
	fun shouldGiveNoViewToSomeoneOutsideTheGame() {
		assertThat(started.viewFor("stranger@example.com")).isNull()
	}

	@Test
	fun shouldGiveTheViewerTheirOwnOffersAndBidOnly() {
		val game = started
			.placeOffer(grace, TAU_CETI, Goods.POLY, OfferType.SELL, 19)
			.placeOffer(ada, BETA_HYDRI, Goods.CHIPS, OfferType.BUY, 3)
			.placeInitiativeBid(grace, 137)
			.placeInitiativeBid(ada, 5)

		val view = game.viewFor(ada)!!

		assertThat(view.player.email).isEqualTo(ada)
		assertThat(view.offers.map { it.playerEmail }).containsExactly(ada)
		assertThat(view.bid!!.amount).isEqualTo(5)
		assertThat(game.viewFor(kim)!!.offers).isEmpty()
		assertThat(game.viewFor(kim)!!.bid).isNull()
	}

	// Grace buys ISO at 11 at Mu Herculis; the counter is at 3, so the market allows 4 u and the game waits for her.
	private val gracesBuy = Offer(grace, MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)
	private val awaitingGrace =
		Resolution(MarketGood(MU_HERCULIS, Goods.ISO), 11, listOf(gracesBuy), null, false, 3, emptyList(), emptyList(), emptyList(), emptyList())

	@Test
	fun shouldGiveTheTradeMaxOnlyToTheTraderTheCounterWaitsFor() {
		// R12, R29: 300 HT at 11 HT and no cap from cargo for a buy, so the market's 4 u
		val game = withTurn { it.copy(resolution = awaitingGrace) }

		assertThat(game.viewFor(grace)!!.tradeMax).isEqualTo(4)
		assertThat(game.viewFor(ada)!!.tradeMax).isNull()
		assertThat(started.viewFor(grace)!!.tradeMax).isNull()
	}

	@Test
	fun shouldGiveTheWholesaleAskOnlyToTheWholesalerAsked() {
		// R37: the counter's trades are over, Grace passed and Ada is asked
		val asking = awaitingGrace.copy(
			trades = listOf(Trade(gracesBuy, 4, 4, null)),
			wholesalers = listOf(grace, ada),
			wholesales = listOf(Wholesale(grace, null, 0, 11))
		)
		val game = withTurn { it.copy(resolution = asking) }

		assertThat(game.viewFor(ada)!!.wholesaleAsk).isEqualTo(asking)
		assertThat(game.viewFor(grace)!!.wholesaleAsk).isNull()
		assertThat(game.viewFor(kim)!!.wholesaleAsk).isNull()
	}

	/** [started] at the start of Opportunity (Kim, then Ada, then Grace), where Ada owns an ISO factory at Tau Ceti. */
	private val opportunity = (1..3).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1, 3)) }.let { game ->
		game.copy(players = game.players.map { if (it.email == ada) it.copy(factories = listOf(Factory(TAU_CETI, Goods.ISO, 6, 0))) else it })
	}

	private val investing = opportunity.advancePhase(FakeDice())

	@Test
	fun shouldOfferTheCollectToAFactoryOwnerWhoHasNotCollectedEvenBeforeTheirTurn() {
		// E06-S03, R102: only while collecting and only with factories; the form can be filled in out of turn
		assertThat(opportunity.turn!!.opportunist()).isEqualTo(kim)
		assertThat(opportunity.viewFor(ada)!!.factoriesToCollect).containsExactly(Factory(TAU_CETI, Goods.ISO, 6, 0))
		assertThat(opportunity.viewFor(grace)!!.factoriesToCollect).isNull()
		val adasTurn = opportunity.finishOpportunities(kim)
		assertThat(adasTurn.collectOutput(ada, emptyList()).viewFor(ada)!!.factoriesToCollect).isNull()
		assertThat(investing.viewFor(ada)!!.factoriesToCollect).isNull()
		assertThat(started.viewFor(ada)!!.factoriesToCollect).isNull()
	}

	@Test
	fun shouldOfferTheInvestmentOnlyInInvestment() {
		// E06-S04, R102: Collect is in Opportunity now, so nothing waits for it
		assertThat(opportunity.viewFor(ada)!!.investment).isNull()
		assertThat(investing.viewFor(ada)!!.investment).isNotNull()
		assertThat(investing.viewFor(grace)!!.investment).isEqualTo(InvestmentView(investing.freeShipNames(), false, false))
		assertThat(started.viewFor(grace)!!.investment).isNull()
	}

	@Test
	fun shouldSendTheInterceptionQuestionOnlyToThePlayersAsked() {
		// R70: Grace is asked about Ada's ship; Ada, the owner, and Kim are not
		val interception = Interception(TAU_CETI, "Wayfarer", ada, listOf(grace), emptyList())
		val game = withTurn { it.copy(interception = interception) }

		assertThat(game.viewFor(grace)!!.interception).isEqualTo(interception)
		assertThat(game.viewFor(ada)!!.interception).isNull()
		assertThat(game.viewFor(kim)!!.interception).isNull()
	}

	// Ada's Wayfarer and Lantern against Grace's Meridian at Tau Ceti; Grace's Meridian intercepted Wayfarer.
	private val battle = Battle(
		TAU_CETI,
		1,
		BattleStage.HITS,
		listOf(BattleShip("Wayfarer", ada), BattleShip("Lantern", ada), BattleShip("Meridian", grace)),
		listOf(BattleInterception("Wayfarer", "Meridian")),
		emptyList(),
		emptyList(),
		listOf(PendingHits("Wayfarer", ada, 2), PendingHits("Meridian", grace, 1)),
		emptyList(),
		emptyList(),
		emptyList()
	)

	@Test
	fun shouldSendTheBattleOnlyToThoseInIt() {
		// R74–R77: a player with a ship in it sees their part; Kim, who has none, doesn't
		val game = withTurn { it.copy(battle = battle) }

		assertThat(game.viewFor(ada)!!.battle).isEqualTo(battle)
		assertThat(game.viewFor(grace)!!.battle).isEqualTo(battle)
		assertThat(game.viewFor(kim)!!.battle).isNull()
	}

	@Test
	fun shouldSendTheBattleToAnOwnerOfPendingHitsWhoseShipsAreAllGone() {
		// R76: Kim's last ship was destroyed this round, but its hits are still theirs to place
		val kimsHits = battle.copy(pendingHits = battle.pendingHits + PendingHits("Drifter", kim, 1))
		val game = withTurn { it.copy(battle = kimsHits) }

		assertThat(game.viewFor(kim)!!.battle).isEqualTo(kimsHits)
	}

	@Test
	fun shouldShowEachPlayerOnlyTheirOwnShipsThatMayBreakAwayAndTheirOwnHits() {
		// R74: Wayfarer is held by Meridian, Lantern isn't; R76: each owner's own hits
		assertThat(battle.breakAwayShipsOf(ada)).containsExactly("Lantern")
		assertThat(battle.breakAwayShipsOf(grace)).containsExactly("Meridian")
		assertThat(battle.pendingHitsOwnedBy(ada)).containsExactly(PendingHits("Wayfarer", ada, 2))
		assertThat(battle.pendingHitsOwnedBy(kim)).isEmpty()
	}

	@Test
	fun shouldSendEachPlayerOnlyTheSalvosThatConcernThem() {
		// R75–R78: Ada fired at Grace; Kim had no part in it
		val salvo = SalvoResult("Wayfarer", ada, "Meridian", grace, false, 1, SalvoOutcome.AWAITING_PLACEMENT, null)
		val game = withTurn { it.copy(battleReport = BattleReport(TAU_CETI, 1, listOf(salvo))) }

		assertThat(game.viewFor(grace)!!.battleReport!!.results).containsExactly(salvo)
		assertThat(game.viewFor(kim)!!.battleReport).isNull()
	}

	@Test
	fun shouldListOnlyWhatTheShipyardsSell() {
		// R56, R60: no military hull or module, and only the three systems with a shipyard
		assertThat(Hull.forSale()).isNotEmpty().noneMatch { it.military }
		assertThat(Module.forSale()).isNotEmpty().noneMatch { it.military }
		assertThat(StarSystem.shipyards()).containsExactly(TAU_CETI, EPSILON_ERIDANI, BETA_HYDRI)
	}
}
