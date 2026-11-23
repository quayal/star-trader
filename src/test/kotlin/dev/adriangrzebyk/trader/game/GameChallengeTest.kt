package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.ADA
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.GRACE
import dev.adriangrzebyk.trader.game.OpportunityGame.Companion.KIM
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R126: challenging a market position in the Opportunity phase. Kim, first in the order, challenges Grace.
class GameChallengeTest {

	private val fixture = OpportunityGame()
	private val chips = MarketPosition(MU_HERCULIS, Goods.CHIPS, MarketTier.DICTATOR)
	private val iso = MarketPosition(TAU_CETI, Goods.ISO, MarketTier.WHOLESALER)

	/** Kim with political 8 and business 5; Grace with political 4, business 5, Dictator of CHIPS at Mu Herculis and Wholesaler of ISO at Tau Ceti. */
	private val game = fixture.with(
		fixture.with(fixture.opportunity, KIM) { it.copy(connections = Connections(8, 5, 0)) },
		GRACE
	) { it.copy(connections = Connections(4, 5, 0), marketPositions = listOf(chips, iso)) }

	private fun d6(roll: Int) = FakeDice(emptyList(), listOf(roll))

	private fun challenge(from: Game = game, roll: Int) =
		from.challengeMarketPosition(KIM, GRACE, MU_HERCULIS, Goods.CHIPS, ConnectionKind.POLITICAL, d6(roll))

	@Test
	fun shouldLowerThePositionOneTierAndTakeTwoReputationOnARollBelowTheDifference() {
		val challenged = challenge(roll = 3)

		val grace = fixture.playerOf(challenged, GRACE)
		assertThat(grace.marketPositions).containsExactly(chips.copy(tier = MarketTier.WHOLESALER), iso)
		assertThat(grace.reputation.value).isEqualTo(18)
		assertThat(fixture.playerOf(challenged, KIM).money).isEqualTo(280)
		assertThat(fixture.playerOf(challenged, KIM).reputation.value).isEqualTo(20)
		assertThat(challenged.log.drop(game.log.size).map { it.text }).containsExactly(
			"Kessler Cargo challenged Vega Lines's Dictator of CHIPS at Mu Herculis (political 8 vs 4), rolled 3: Vega Lines is demoted to Wholesaler",
			"Vega Lines's reputation: −2 for losing a market position to a challenge → 18"
		)
	}

	@Test
	fun shouldCostTheChallengerTheReputationItMissedByOnARollAtOrAboveTheDifference() {
		// Difference 4, roll 5: missed 1–3 by 2 (the creator's example).
		val challenged = challenge(roll = 5)

		assertThat(fixture.playerOf(challenged, GRACE).marketPositions).containsExactly(chips, iso)
		assertThat(fixture.playerOf(challenged, GRACE).reputation.value).isEqualTo(20)
		assertThat(fixture.playerOf(challenged, KIM).money).isEqualTo(280)
		assertThat(fixture.playerOf(challenged, KIM).reputation.value).isEqualTo(18)
		assertThat(challenged.log.drop(game.log.size).map { it.text }).containsExactly(
			"Kessler Cargo challenged Vega Lines's Dictator of CHIPS at Mu Herculis (political 8 vs 4), rolled 5: the challenge fails",
			"Kessler Cargo's reputation: −2 for a failed challenge → 18"
		)
	}

	@Test
	fun shouldGiveBothPlayersACardAndTheOthersTheLine() {
		val won = challenge(roll = 1).log.drop(game.log.size)
		val lost = challenge(roll = 6).log.drop(game.log.size)

		assertThat(won.map { it.notice }).containsOnly(Notice.setback(NoticeKind.CHALLENGE_SUCCEEDED, MU_HERCULIS, listOf(KIM, GRACE)))
		assertThat(lost.map { it.notice }).containsOnly(Notice.setback(NoticeKind.CHALLENGE_FAILED, MU_HERCULIS, listOf(KIM, GRACE)))
	}

	@Test
	fun shouldLoseARetailerPositionAltogether() {
		val retailer = fixture.with(game, GRACE) {
			it.copy(marketPositions = listOf(MarketPosition(MU_HERCULIS, Goods.CHIPS, MarketTier.RETAILER)))
		}

		val challenged = challenge(retailer, 1)

		assertThat(fixture.playerOf(challenged, GRACE).marketPositions).isEmpty()
		assertThat(challenged.log.drop(retailer.log.size).first().text).endsWith("rolled 1: Vega Lines loses the position")
	}

	@Test
	fun shouldFightWithBusinessConnectionsWhenChosen() {
		// Business 5 vs 5: a difference of 0 can't succeed; political 8 vs 4 can.
		assertThatThrownBy { game.challengeMarketPosition(KIM, GRACE, MU_HERCULIS, Goods.CHIPS, ConnectionKind.BUSINESS, d6(1)) }
			.isInstanceOf(InvalidChallengeException::class.java)
			.hasMessage("Your business connections (5) must beat Vega Lines's (5) by at least 2 for a challenge to have a chance.")
		val stronger = fixture.with(game, KIM) { it.copy(connections = Connections(8, 9, 0)) }

		val challenged = stronger.challengeMarketPosition(KIM, GRACE, TAU_CETI, Goods.ISO, ConnectionKind.BUSINESS, d6(3))

		// Grace was Wholesaler of ISO: one tier down is Retailer.
		assertThat(fixture.playerOf(challenged, GRACE).marketPositions)
			.containsExactly(chips, MarketPosition(TAU_CETI, Goods.ISO, MarketTier.RETAILER))
		assertThat(challenged.log.drop(game.log.size).first().text).contains("(business 9 vs 5)")
	}

	@Test
	fun shouldRefuseWhatTheRulesDoNotAllow() {
		assertThatThrownBy { game.challengeMarketPosition(KIM, KIM, MU_HERCULIS, Goods.CHIPS, ConnectionKind.POLITICAL, d6(1)) }
			.isInstanceOf(InvalidChallengeException::class.java)
			.hasMessage("Choose another player to challenge.")
		assertThatThrownBy { game.challengeMarketPosition(KIM, "nobody@example.com", MU_HERCULIS, Goods.CHIPS, ConnectionKind.POLITICAL, d6(1)) }
			.hasMessage("Choose another player to challenge.")
		assertThatThrownBy { game.challengeMarketPosition(KIM, GRACE, MU_HERCULIS, Goods.ISO, ConnectionKind.POLITICAL, d6(1)) }
			.hasMessage("Vega Lines holds no market position for ISO at Mu Herculis.")
		assertThatThrownBy { game.challengeMarketPosition(KIM, GRACE, MU_HERCULIS, Goods.CHIPS, ConnectionKind.CRIMINAL, d6(1)) }
			.hasMessage("Challenge with political or business connections.")
		assertThatThrownBy { fixture.with(game, KIM) { it.copy(money = 19) }.let { challenge(it, 1) } }
			.hasMessage("Challenging a market position costs 20 HT; you have 19 HT.")
		assertThatThrownBy { game.challengeMarketPosition(ADA, GRACE, MU_HERCULIS, Goods.CHIPS, ConnectionKind.POLITICAL, d6(1)) }
			.isInstanceOf(NotYourTurnException::class.java)
			.hasMessage("It's Kessler Cargo's Opportunity turn.")
	}

	@Test
	fun shouldAllowOneChallengePerOpportunity() {
		assertThatThrownBy { challenge(roll = 5).let { challenge(it, 1) } }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("You've already challenged a market position this Opportunity.")
	}

	@Test
	fun shouldTellTheChallengerTheyHaveChallenged() {
		assertThat(game.viewFor(KIM)!!.opportunity!!.challenged).isFalse()
		assertThat(challenge(roll = 5).viewFor(KIM)!!.opportunity!!.challenged).isTrue()
		assertThat(challenge(roll = 5).viewFor(GRACE)!!.opportunity!!.challenged).isFalse()
	}
}
