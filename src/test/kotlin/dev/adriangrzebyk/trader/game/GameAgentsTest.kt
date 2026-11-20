package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.ADA
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.GRACE
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import dev.adriangrzebyk.trader.game.agent.Agent
import dev.adriangrzebyk.trader.game.agent.AgentPlace
import dev.adriangrzebyk.trader.game.agent.AgentPosition
import dev.adriangrzebyk.trader.game.agent.InvalidAgentException
import dev.adriangrzebyk.trader.game.agent.RecruitedAgent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R103–R108 (D25): agents. Kim is first in the Opportunity order, with Bright Reach in Port at Tau Ceti.
class GameAgentsTest {

	private val fixture = OpportunityGame()
	private val game = fixture.opportunity
	private val aboard = AgentPosition.Aboard("Bright Reach")

	/** Dice for a recruit: [roll] (2d6, × 5 against the payment) and [pick], the place in the pool drawn. */
	private fun recruitDice(roll: Int, pick: Int = 1) = FakeDice(listOf(roll), oneOfRolls = listOf(pick))

	@Test
	fun shouldRecruitARandomAgentFromThePoolWhenTheRollIsAtOrBelowThePayment() {
		// R103: 7 × 5 = 35 ≤ 35; the pool is eight without Badger and Frank (R108), Novak first
		assertThat(game.agentPool()).hasSize(8).doesNotContain(Agent.BADGER, Agent.FRANK)

		val recruited = game.recruitAgent(KIM, 35, aboard, recruitDice(7))

		assertThat(fixture.playerOf(recruited, KIM).agents).containsExactly(RecruitedAgent(Agent.NOVAK, aboard))
		assertThat(fixture.playerOf(recruited, KIM).money).isEqualTo(265)
		assertThat(recruited.agentPool()).hasSize(7).doesNotContain(Agent.NOVAK)
		assertThat(recruited.log.last().text).isEqualTo("Kessler Cargo recruited an agent at Tau Ceti")
		assertThatThrownBy { recruited.recruitAgent(KIM, 60, aboard, recruitDice(2)) }.hasMessage("You've already recruited this Opportunity.")
	}

	@Test
	fun shouldTakeTheMoneyAndLogNothingWhenTheRollIsAbove() {
		val failed = game.recruitAgent(KIM, 35, aboard, recruitDice(8))

		assertThat(fixture.playerOf(failed, KIM).agents).isEmpty()
		assertThat(fixture.playerOf(failed, KIM).money).isEqualTo(265)
		assertThat(failed.log).isEqualTo(game.log)
	}

	@Test
	fun shouldRefuseAPaymentOutsideTenToSixtyOrAStartWithoutAShipOrWarehouse() {
		assertThatThrownBy { game.recruitAgent(KIM, 61, aboard, recruitDice(2)) }
			.isInstanceOf(InvalidAgentException::class.java)
			.hasMessage("Pay a whole number from 10 to 60 HT.")
		assertThatThrownBy { game.recruitAgent(KIM, 30, AgentPosition.OnOwn(SIGMA_DRACONIS, ShipLocation.PORT), recruitDice(2)) }
			.hasMessage("You need a ship or a warehouse at Sigma Draconis.")
		assertThatThrownBy { game.recruitAgent(ADA, 30, AgentPosition.Aboard("Wayfarer"), recruitDice(2)) }
			.isInstanceOf(NotYourTurnException::class.java)
	}

	@Test
	fun shouldStepOffAndBoardAtTheSamePlaceOnly() {
		// R104
		val recruited = game.recruitAgent(KIM, 60, aboard, recruitDice(2))
		val off = recruited.moveAgent(KIM, Agent.NOVAK, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT))

		assertThat(fixture.playerOf(off, KIM).agentsOnOwn().single().place).isEqualTo(AgentPlace(TAU_CETI, ShipLocation.PORT))
		assertThat(fixture.playerOf(off, KIM).agentsAboard("Bright Reach")).isZero()
		assertThat(fixture.playerOf(off.moveAgent(KIM, Agent.NOVAK, aboard), KIM).agentsAboard("Bright Reach")).isEqualTo(1)
		assertThatThrownBy { off.moveAgent(KIM, Agent.NOVAK, AgentPosition.Aboard("Corvid")) }
			.hasMessage("Novak can board only your ships at the same place.")
		assertThatThrownBy { off.moveAgent(KIM, Agent.NOVAK, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PLANET)) }
			.hasMessage("Novak can step off only where his ship is, in Port or on the Planet.")
	}

	@Test
	fun shouldMoveAnAgentAtAnyTimeLikeCargoNotOnlyOnTheOwnersOpportunityTurn() {
		// R104 (playtest, 2026-11-20): Kim has finished his Opportunity turn and still moves Novak, and so would he in Investment
		val done = game.recruitAgent(KIM, 60, aboard, recruitDice(2)).finishOpportunities(KIM)

		val off = done.moveAgent(KIM, Agent.NOVAK, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT))

		assertThat(fixture.playerOf(off, KIM).agentsAboard("Bright Reach")).isZero()
	}

	@Test
	fun shouldRefuseMovingAnAgentAboardAShipInSpace() {
		// R104: an agent is never on its own in Space, and nobody changes ships there, as with cargo
		val recruited = game.recruitAgent(KIM, 60, aboard, recruitDice(2))
		val inSpace = fixture.with(recruited, KIM) { player ->
			player.copy(ships = player.ships.map { if (it.name == "Bright Reach") it.copy(location = ShipLocation.SPACE) else it })
		}

		assertThatThrownBy { inSpace.moveAgent(KIM, Agent.NOVAK, AgentPosition.Aboard("Corvid")) }
			.isInstanceOf(InvalidAgentException::class.java)
			.hasMessage("Agents can't move in Space: wait until the ship is in Port or on the Planet.")
	}

	@Test
	fun shouldSellAnAgentWhoseIdentityOnlyTheSellerSeesUntilItsBought() {
		// R106: the buyer may answer any time before Opportunity closes
		val offered = game.recruitAgent(KIM, 60, aboard, recruitDice(2)).offerAgent(KIM, Agent.NOVAK, ADA, 25).finishOpportunities(KIM)

		assertThat(offered.viewFor(KIM)!!.agentOffers.single().agent).isEqualTo(Agent.NOVAK)
		assertThat(offered.viewFor(ADA)!!.agentOffers.single().agent).isNull()
		assertThat(offered.viewFor(ADA)!!.agentOffers.single().place).isEqualTo(AgentPlace(TAU_CETI, ShipLocation.PORT))
		assertThat(offered.viewFor(GRACE)!!.agentOffers).isEmpty()
		assertThatThrownBy { offered.answerAgentOffer(GRACE, 1, true) }.hasMessage("This offer isn't for you.")

		val bought = offered.answerAgentOffer(ADA, 1, true)

		assertThat(
			fixture.playerOf(bought, ADA).agents
		).containsExactly(RecruitedAgent(Agent.NOVAK, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT)))
		assertThat(fixture.playerOf(bought, ADA).money).isEqualTo(275)
		assertThat(fixture.playerOf(bought, KIM).agents).isEmpty()
		assertThat(fixture.playerOf(bought, KIM).money).isEqualTo(265)
		assertThat(bought.log.last().text).isEqualTo("Kessler Cargo sold an agent at Tau Ceti to Orion Freight")
		assertThat(bought.turn!!.agentOffers).isEmpty()
	}

	@Test
	fun shouldLetOffersExpireWhenOpportunityCloses() {
		val offered = game.recruitAgent(KIM, 60, aboard, recruitDice(2)).offerAgent(KIM, Agent.NOVAK, ADA, 25)

		val investing = OpportunityPassed(offered).byEveryone()

		assertThat(investing.turn!!.agentOffers).isEmpty()
		assertThatThrownBy { investing.answerAgentOffer(ADA, 1, true) }.hasMessage("Opportunity isn't open.")
	}

	@Test
	fun shouldAddWolfsFourToHisOwnersInitiativeAndRevealHim() {
		// R108: Ada rolls 2, Grace 3, Kim 1 + 4 = 5
		val wolf = fixture.with(fixture.started, KIM) { it.copy(agents = listOf(RecruitedAgent(Agent.WOLF, aboard))) }

		val closed = wolf.advancePhase(FakeDice(2, 3, 1))

		assertThat(closed.turn!!.initiative).containsExactly(KIM, GRACE, ADA)
		assertThat(closed.turn!!.initiativeRolls.first { it.playerEmail == KIM }.total()).isEqualTo(5)
		assertThat(closed.log.takeLast(2).map { it.text }).containsExactly(
			"Wolf (Kessler Cargo): +4 initiative",
			"Initiative (bid + 2d6): Kessler Cargo 0 + 1 + Wolf 4 = 5 → Vega Lines 0 + 3 = 3 → Orion Freight 0 + 2 = 2"
		)
	}

	@Test
	fun shouldKillTheAgentsAboardAShipDestroyedInBattleForGood() {
		// R107
		val recruited = fixture.playerOf(game.recruitAgent(KIM, 60, aboard, recruitDice(2)), KIM)

		assertThat(recruited.agentsKilledLog("Bright Reach")).isEqualTo("1 agent aboard Bright Reach was killed")
		assertThat(recruited.loseShip("Bright Reach").agents).isEmpty()
		assertThat(game.copy(deadAgents = listOf(Agent.NOVAK)).agentPool()).doesNotContain(Agent.NOVAK)
	}

	@Test
	fun shouldStepAgentsOffASoldShip() {
		// R107: a ship sold in Port leaves its agents in that Port
		val kim = fixture.playerOf(game, KIM).copy(agents = listOf(RecruitedAgent(Agent.X, aboard)))

		assertThat(kim.sellShip("Bright Reach").agents).containsExactly(RecruitedAgent(Agent.X, AgentPosition.OnOwn(TAU_CETI, ShipLocation.PORT)))
	}

	@Test
	fun shouldGiveTheShipCarryingThemTheirBonusesOnly() {
		// R108: Novak, Trigger and X act on the ship they ride
		val kim = fixture.playerOf(game, KIM).copy(
			agents = listOf(
				RecruitedAgent(Agent.NOVAK, aboard),
				RecruitedAgent(Agent.TRIGGER, aboard),
				RecruitedAgent(Agent.X, AgentPosition.Aboard("Corvid"))
			)
		)

		assertThat(kim.jumpBonusFor("Bright Reach")).isEqualTo(4)
		assertThat(kim.interceptBonusFor("Bright Reach")).isEqualTo(1)
		assertThat(kim.weaponsBonusFor("Bright Reach")).isZero()
		assertThat(kim.weaponsBonusFor("Corvid")).isEqualTo(5)
		assertThat(kim.jumpBonusFor("Corvid")).isZero()
	}

	@Test
	fun shouldAskLimpysOwnerFirstAndLetThemActAsDictator() {
		// R108: Kim, first in initiative, resolves; Limpy is in Port at the first good's system, which has no Dictator
		val good = fixture.trading.turn!!.tradingQueue.first()
		val limpy = fixture.with(fixture.trading, KIM) {
			it.copy(agents = listOf(RecruitedAgent(Agent.LIMPY, AgentPosition.OnOwn(good.system, ShipLocation.PORT))))
		}

		val asked = limpy.resolveNextGood(KIM, FakeDice())
		assertThat(asked.turn!!.limpyAsk).isEqualTo(KIM)
		assertThat(asked.viewFor(KIM)!!.limpyAsk).isEqualTo(good)

		val acting = asked.answerLimpy(KIM, true, FakeDice())
		assertThat(acting.turn!!.resolution!!.awaitingDictator()).isEqualTo(KIM)
		assertThat(acting.turn!!.limpyUsedBy).containsExactly(KIM)
		assertThat(acting.log.map { it.text }).contains("Limpy (Kessler Cargo) lets them act as Dictator of ${good.label()}")

		val passed = asked.answerLimpy(KIM, false, FakeDice.twoD6(7))
		assertThat(passed.turn!!.resolution!!.dictator).isNull()
		assertThat(passed.turn!!.limpyUsedBy).isEmpty()
	}
}
