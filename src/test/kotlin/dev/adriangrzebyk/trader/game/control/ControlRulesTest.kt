package dev.adriangrzebyk.trader.game.control

import dev.adriangrzebyk.trader.game.Connections
import dev.adriangrzebyk.trader.game.Reputation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R128, R129: the Control phase's small rules, on the classes that own their data.
class ControlRulesTest {

	@Test
	fun shouldEvadePoliceOnlyAboveItsValue() {
		assertThat(PoliceRoll(5, 0, 5).evaded()).isFalse()
		assertThat(PoliceRoll(6, 0, 5).evaded()).isTrue()
		assertThat(PoliceRoll(4, 2, 5).evaded()).isTrue()
		assertThat(PoliceRoll(4, 2, 5).text()).isEqualTo("4 + 2 = 6 vs police 5")
		assertThat(PoliceRoll(5, 0, 5).text()).isEqualTo("5 vs police 5")
	}

	@Test
	fun shouldReadTheVerdictTableAfterTheReduction() {
		val results = (1..6).map { ControlVerdict(it, 0).result() }

		assertThat(results).containsExactly(
			ControlResult.INNOCENT,
			ControlResult.SMALL_FINE,
			ControlResult.LARGE_FINE,
			ControlResult.TAKEN_OVER,
			ControlResult.CLOSED,
			ControlResult.CLOSED
		)
		assertThat(ControlVerdict(3, 2).result()).isEqualTo(ControlResult.INNOCENT)
		assertThat(ControlVerdict(6, 3).text()).isEqualTo("6 − 3 = 3")
	}

	@Test
	fun shouldTakeOnlyTheEliminatingResultsFromABacker() {
		assertThat(ControlResult.entries.filter { it.eliminated }).containsExactly(ControlResult.TAKEN_OVER, ControlResult.CLOSED)
		assertThat(ControlResult.entries.filter { !it.eliminated }.all { it.backerPoliticalLost == 0 && it.backerOthersLost == 0 }).isTrue()
	}

	@Test
	fun shouldAskEveryoneUntilSomeoneBacksThenAskNobodyElse() {
		val audit = ControlAudit("a", listOf("b", "c"), null)

		assertThat(audit.asks("b")).isTrue()
		assertThat(audit.asks("a")).isFalse()
		val declined = audit.answered("b", false)
		assertThat(declined.asked).containsExactly("c")
		assertThat(declined.isDecided()).isFalse()
		assertThat(declined.answered("c", false).isDecided()).isTrue()
		val backed = audit.answered("c", true)
		assertThat(backed.backer).isEqualTo("c")
		assertThat(backed.isDecided()).isTrue()
		assertThat(backed.asks("b")).isFalse()
	}

	@Test
	fun shouldExamineAReputationOfFiveOrBelowAndFailItOnAHigherRoll() {
		assertThat(listOf(0, 5, 6).map { Reputation(it).isUnderControl() }).containsExactly(true, true, false)
		assertThat(Reputation(5).failsControl(5)).isFalse()
		assertThat(Reputation(5).failsControl(6)).isTrue()
		assertThat(Reputation(0).failsControl(1)).isTrue()
	}

	@Test
	fun shouldTakeOffOneFromFiveToEightPoliticalAndTwoFromNine() {
		assertThat(listOf(0, 4, 5, 8, 9, 10).map { Connections(it, 0, 0).controlReduction() }).containsExactly(0, 0, 1, 1, 2, 2)
	}

	@Test
	fun shouldLowerConnectionsWithoutGoingBelowZero() {
		assertThat(Connections(6, 4, 1).lowered(4, 2)).isEqualTo(Connections(2, 2, 0))
		assertThat(Connections(3, 4, 1).lowered(10, 3)).isEqualTo(Connections(0, 1, 0))
	}
}
