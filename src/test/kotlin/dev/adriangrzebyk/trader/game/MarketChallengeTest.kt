package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R126: the odds of challenging a market position.
class MarketChallengeTest {

	private val challenge = MarketChallenge(ConnectionKind.POLITICAL, Connections(8, 1, 0), Connections(4, 9, 0))

	@Test
	fun shouldTakeTheDifferenceOfTheChosenConnection() {
		assertThat(challenge.difference()).isEqualTo(4)
		assertThat(MarketChallenge(ConnectionKind.BUSINESS, Connections(8, 1, 0), Connections(4, 9, 0)).difference()).isEqualTo(-8)
	}

	@Test
	fun shouldSucceedOnARollBelowTheDifference() {
		assertThat((1..6).filter { challenge.succeeds(it) }).containsExactly(1, 2, 3)
	}

	@Test
	fun shouldCostTheReputationByWhichTheRollMissed() {
		// Success is 1–3, so a 5 missed by 2 (R126's example), a 4 by 1 and a 6 by 3.
		assertThat(challenge.reputationLost(4)).isEqualTo(1)
		assertThat(challenge.reputationLost(5)).isEqualTo(2)
		assertThat(challenge.reputationLost(6)).isEqualTo(3)
	}

	@Test
	fun shouldNotBeWorthRollingBelowADifferenceOfTwo() {
		assertThat(MarketChallenge(ConnectionKind.POLITICAL, Connections(5, 0, 0), Connections(4, 0, 0)).canSucceed()).isFalse()
		assertThat(MarketChallenge(ConnectionKind.POLITICAL, Connections(6, 0, 0), Connections(4, 0, 0)).canSucceed()).isTrue()
	}

	@Test
	fun shouldWordTheLogLine() {
		val position = MarketPosition(StarSystem.MU_HERCULIS, Goods.CHIPS, MarketTier.DICTATOR)

		assertThat(challenge.logText("Evil Corp", "Star Dust", position, 2))
			.isEqualTo(
				"Evil Corp challenged Star Dust's Dictator of CHIPS at Mu Herculis (political 8 vs 4), rolled 2: Star Dust is demoted to Wholesaler"
			)
		assertThat(challenge.logText("Evil Corp", "Star Dust", position.copy(tier = MarketTier.RETAILER), 2))
			.endsWith("rolled 2: Star Dust loses the position")
		assertThat(challenge.logText("Evil Corp", "Star Dust", position, 5))
			.isEqualTo("Evil Corp challenged Star Dust's Dictator of CHIPS at Mu Herculis (political 8 vs 4), rolled 5: the challenge fails")
	}
}
