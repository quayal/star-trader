package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.ADA
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.GRACE
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import dev.adriangrzebyk.trader.game.sabotage.InvalidSabotageException
import dev.adriangrzebyk.trader.game.sabotage.SabotageTarget
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R97–R101 (D23): sabotage in the Opportunity phase. Kim, first in the order, sabotages Grace's assets.
class GameSabotageTest {

	private val fixture = OpportunityGame()

	/** Kim with criminal connections 5; Grace with an ISO factory of 2 and a warehouse holding 4 ISO and 3 POLY at Tau Ceti. */
	private val game = fixture.with(
		fixture.with(fixture.opportunity, KIM) { it.copy(connections = Connections(3, 3, 5)) },
		GRACE
	) {
		it.copy(
			factories = listOf(Factory(TAU_CETI, Goods.ISO, 2, 0)),
			warehouses = listOf(Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 4, Goods.POLY to 3)))
		)
	}

	private val factory = SabotageTarget.Factory(GRACE, TAU_CETI, Goods.ISO)
	private val warehouse = SabotageTarget.Warehouse(GRACE, TAU_CETI)

	@Test
	fun shouldDisableAFactoryAndCostTheSaboteurReputation() {
		// Effect 2d6 12 + 5 criminal − 2 units = 15: 3 turns; reputation 9 + 5 = 14: −6. Tau Ceti's legal system is 2: 40 HT.
		val sabotaged = game.sabotage(KIM, factory, ADA, FakeDice(12, 9))

		assertThat(fixture.playerOf(sabotaged, GRACE).factoryAt(TAU_CETI, Goods.ISO)!!.disabledTurns).isEqualTo(3)
		assertThat(fixture.playerOf(sabotaged, KIM).money).isEqualTo(260)
		assertThat(fixture.playerOf(sabotaged, KIM).reputation.value).isEqualTo(14)
		assertThat(sabotaged.log.drop(game.log.size).map { it.text }).containsExactly(
			"Sabotage at Tau Ceti: Vega Lines's ISO factory disabled for 3 turns",
			"Kessler Cargo's reputation: −6 for sabotage (rolled 2d6+5: 14) → 14"
		)
		assertThat(sabotaged.log.drop(game.log.size).first().notice!!.players).containsExactly(GRACE)
		assertThat(sabotaged.log.last().notice).isEqualTo(Notice.setback(NoticeKind.SABOTAGE_TRACED, null, listOf(KIM)))
	}

	@Test
	fun shouldBlameTheInnocentPlayerOnTwentyOneOrMore() {
		// R100: reputation 12 + 9 criminal = 21: Kim loses nothing, Ada loses 8
		val criminal = fixture.with(game, KIM) { it.copy(connections = Connections(3, 3, 9)) }

		val sabotaged = criminal.sabotage(KIM, factory, ADA, FakeDice(2, 12))

		assertThat(fixture.playerOf(sabotaged, KIM).reputation.value).isEqualTo(20)
		assertThat(fixture.playerOf(sabotaged, ADA).reputation.value).isEqualTo(12)
		assertThat(sabotaged.log.last().text).isEqualTo("Orion Freight's reputation: −8 for being blamed for sabotage → 12")
		assertThat(sabotaged.log.last().notice).isEqualTo(Notice.setback(NoticeKind.SABOTAGE_BLAMED, null, listOf(ADA)))
		// Effect 2 + 9 − 2 = 9: no effect, so no sabotage line that would point at anyone.
		assertThat(sabotaged.log.map { it.text }).noneMatch { it.startsWith("Sabotage at") }
	}

	@Test
	fun shouldRefuseWhatTheRulesDoNotAllow() {
		assertThatThrownBy { game.sabotage(KIM, SabotageTarget.Warehouse(KIM, TAU_CETI), ADA, FakeDice()) }
			.isInstanceOf(InvalidSabotageException::class.java)
			.hasMessage("You can't sabotage your own assets.")
		assertThatThrownBy { game.sabotage(KIM, factory, GRACE, FakeDice()) }
			.hasMessage("Choose an innocent player to take the blame: not yourself or the target.")
		assertThatThrownBy { game.sabotage(KIM, SabotageTarget.Ship(GRACE, TAU_CETI, "Meridian"), ADA, FakeDice()) }
			.hasMessage("Choose a ship in Port: ships in the yard, on a Planet or in Space can't be sabotaged.")
		assertThatThrownBy { fixture.with(game, KIM) { it.copy(money = 30) }.sabotage(KIM, factory, ADA, FakeDice()) }
			.hasMessage("Sabotage at Tau Ceti costs 40 HT; you have 30 HT.")
		assertThatThrownBy { game.sabotage(ADA, factory, KIM, FakeDice()) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Kessler Cargo's Opportunity turn.")
		assertThatThrownBy { game.sabotage(KIM, factory, ADA, FakeDice(2, 2)).sabotage(KIM, warehouse, ADA, FakeDice()) }
			.hasMessage("You've already sabotaged this Opportunity.")
	}

	@Test
	fun shouldLetTheSaboteurChooseTheMissingUnitsAndWaitForIt() {
		// R99: effect 10 + 5 = 15: 3 units; the warehouse holds 7, so Kim chooses which
		val sabotaged = game.sabotage(KIM, warehouse, ADA, FakeDice(10, 2))

		assertThat(sabotaged.viewFor(KIM)!!.sabotagedWarehouse).isEqualTo(Warehouse(TAU_CETI, 1, mapOf(Goods.ISO to 4, Goods.POLY to 3)))
		assertThat(sabotaged.viewFor(ADA)!!.sabotagedWarehouse).isNull()
		assertThatThrownBy { sabotaged.finishOpportunities(KIM) }.hasMessage("Choose the units missing from the warehouse first.")
		assertThatThrownBy { sabotaged.chooseSabotageLosses(KIM, mapOf(Goods.ISO to 2)) }.hasMessage("Choose 3 units in all.")

		val chosen = sabotaged.chooseSabotageLosses(KIM, mapOf(Goods.ISO to 2, Goods.POLY to 1))

		assertThat(fixture.playerOf(chosen, GRACE).warehouseAt(TAU_CETI)!!.cargo).isEqualTo(mapOf(Goods.ISO to 2, Goods.POLY to 2))
		assertThat(chosen.viewFor(GRACE)!!.sabotagesSuffered.single().lost).isEqualTo(mapOf(Goods.ISO to 2, Goods.POLY to 1))
		assertThat(chosen.viewFor(GRACE)!!.sabotagesOrdered).isEmpty()
		assertThat(chosen.viewFor(ADA)!!.sabotagesSuffered).isEmpty()
		assertThat(chosen.log.map { it.text }).contains("Sabotage at Tau Ceti: Vega Lines's warehouse lost 3 u")
		assertThat(chosen.finishOpportunities(KIM).turn!!.opportunist()).isEqualTo(ADA)
	}

	@Test
	fun shouldWaitForTheShipsOwnerToPlaceTheHitsBeforeInvestment() {
		// R99, R102: effect 2 on Meridian in Port at Sigma Draconis (legal system 1 since 2026-11-20: 20 HT)
		val ship = SabotageTarget.Ship(GRACE, SIGMA_DRACONIS, "Meridian")
		val sabotaged = game.sabotage(KIM, ship, ADA, FakeDice(8, 2))
		val everyoneDone = sabotaged.finishOpportunities(KIM).finishOpportunities(ADA)
			.collectOutput(GRACE, emptyList()).finishOpportunities(GRACE)

		assertThat(everyoneDone.turn!!.phase).isEqualTo(TurnPhase.OPPORTUNITY)
		assertThat(everyoneDone.turn!!.owedChoices()).containsExactly(GRACE)
		val placed = everyoneDone.placeSabotageHits(GRACE, "Meridian", HitPlacement(0, listOf(ModuleHit(Module.CARGO, false, 2)), emptyMap()))

		assertThat(placed.turn!!.phase).isEqualTo(TurnPhase.INVESTMENT)
		assertThat(fixture.playerOf(placed, GRACE).ship("Meridian")!!.modules.count { it == Module.CARGO }).isEqualTo(3)
		assertThat(fixture.playerOf(placed, KIM).money).isEqualTo(280)
	}

	@Test
	fun shouldBeFoiledBySilentInPortThereAfterThePlayerPaid() {
		// R108: no rolls (the dice have none), no effect, no reputation loss; Silent is revealed
		val silent = fixture.with(game, GRACE) {
			it.copy(agents = listOf(RecruitedAgent(Agent.SILENT, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT))))
		}

		val foiled = silent.sabotage(KIM, factory, ADA, FakeDice())

		assertThat(fixture.playerOf(foiled, KIM).money).isEqualTo(260)
		assertThat(fixture.playerOf(foiled, KIM).reputation.value).isEqualTo(20)
		assertThat(fixture.playerOf(foiled, GRACE).factoryAt(TAU_CETI, Goods.ISO)!!.disabledTurns).isZero()
		assertThat(foiled.log.last().text).isEqualTo("Silent (Vega Lines) foiled a sabotage at Tau Ceti")
	}

	@Test
	fun shouldAddShortysFourToBothRollsWhenHeIsAtTheTargetsSystem() {
		// R108: Shorty aboard Bright Reach at Tau Ceti; effect 8 + 5 + 4 − 2 = 15: 3 turns; reputation 6 + 5 + 4 = 15: −4
		val shorty = fixture.with(game, KIM) { it.copy(agents = listOf(RecruitedAgent(Agent.SHORTY, AgentPosition.Aboard("Bright Reach")))) }

		val sabotaged = shorty.sabotage(KIM, factory, ADA, FakeDice(8, 6))

		assertThat(fixture.playerOf(sabotaged, GRACE).factoryAt(TAU_CETI, Goods.ISO)!!.disabledTurns).isEqualTo(3)
		assertThat(sabotaged.log.drop(shorty.log.size).map { it.text }).containsExactly(
			"Shorty (Kessler Cargo): +4 to a sabotage at Tau Ceti",
			"Sabotage at Tau Ceti: Vega Lines's ISO factory disabled for 3 turns",
			"Kessler Cargo's reputation: −4 for sabotage (rolled 2d6+9: 15) → 16"
		)
	}

	@Test
	fun shouldMakeEveryReputationLossOneSmallerWithJbInPort() {
		// R108: JB in Port at Tau Ceti; −6 becomes −5, and JB is revealed
		val jb = fixture.with(game, KIM) { it.copy(agents = listOf(RecruitedAgent(Agent.JB, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT)))) }

		val sabotaged = jb.sabotage(KIM, factory, ADA, FakeDice(12, 9))

		assertThat(fixture.playerOf(sabotaged, KIM).reputation.value).isEqualTo(15)
		assertThat(sabotaged.log.takeLast(2).map { it.text }).containsExactly(
			"Sabotage at Tau Ceti: Vega Lines's ISO factory disabled for 3 turns",
			"Kessler Cargo's reputation: −6 + JB 1 = −5 for sabotage (rolled 2d6+5: 14) → 15"
		)
	}
}
