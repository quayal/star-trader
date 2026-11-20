package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.game.ConnectionKind
import dev.adriangrzebyk.trader.game.Connections
import dev.adriangrzebyk.trader.game.FakeDice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// R109–R111: the creator's 40 tip-offs, drawn, learned, taken and announced.
class NewsTest {

	@Test
	fun shouldHoldTheCreatorsTwentySixOpportunitiesAndFourteenEvents() {
		assertThat(TipOff.entries.count { it.opportunity != null }).isEqualTo(26)
		assertThat(TipOff.entries.count { it.event != null }).isEqualTo(14)
		assertThat(
			TipOff.entries.filterNot {
				it.returnsToPool()
			}
		).containsExactly(TipOff.E2, TipOff.E4, TipOff.E7, TipOff.E8, TipOff.E9, TipOff.E10, TipOff.E14)
		assertThat(TipOff.O1.need.text()).isEqualTo("CRIM 4")
		assertThat(TipOff.O9.need.text()).isEqualTo("BUSS 10")
		assertThat(TipOff.entries.all { it.lead >= 1 }).isTrue()
	}

	@Test
	fun shouldDrawD3TipOffsAtRandomFromThePoolIntoNumberedSlots() {
		// d3 rolls 2; oneOf picks the 1st of 40 (O1), then the 3rd of the 39 left (O4)
		val draw = News.full().drawIn(5, FakeDice(emptyList(), emptyList(), listOf(2), listOf(1, 3)))

		assertThat(draw.roll).isEqualTo(2)
		assertThat(draw.drawn).containsExactly(ActiveTipOff(1, TipOff.O1, 5, emptyList(), null), ActiveTipOff(2, TipOff.O4, 5, emptyList(), null))
		assertThat(draw.news.pool).hasSize(38).doesNotContain(TipOff.O1, TipOff.O4)
		assertThat(draw.news.nextSlot).isEqualTo(3)
	}

	@Test
	fun shouldRollNothingWithAnEmptyPool() {
		val draw = News.NONE.drawIn(5, FakeDice(emptyList()))

		assertThat(draw.roll).isNull()
		assertThat(draw.news.hasTipOffsInPlay()).isFalse()
	}

	@Test
	fun shouldMakeATipOffEffectiveItsLeadAfterItsDrawAndReturnItToThePoolUnlessItsAOneTimeEvent() {
		// O1 has lead 3, E2 (one time only) lead 1
		val news = News.NONE.copy(
			inPlay = listOf(ActiveTipOff(1, TipOff.O1, 5, emptyList(), null), ActiveTipOff(2, TipOff.E2, 5, emptyList(), null))
		)

		assertThat(news.dueIn(6).map { it.tipOff }).containsExactly(TipOff.E2)
		val after = news.afterEffectiveIn(6)
		assertThat(after.retired).containsExactly(TipOff.E2)
		assertThat(after.inPlay.map { it.tipOff }).containsExactly(TipOff.O1)
		assertThat(after.afterEffectiveIn(8).pool).containsExactly(TipOff.O1)
	}

	@Test
	fun shouldNeedTheConnectionLevelNow() {
		assertThat(TipOff.O1.need.isMetBy(Connections(0, 0, 4))).isTrue()
		assertThat(TipOff.O1.need.isMetBy(Connections(9, 9, 3))).isFalse()
		assertThat(TipOff.O1.need.refusal(Connections(9, 9, 3))).isEqualTo("You need CRIM 4 to learn this (yours: 3).")
		assertThat(TipOffNeed(ConnectionKind.POLITICAL, 5).text()).isEqualTo("POL 5")
	}

	@Test
	fun shouldShowATakenLegalOpportunityToEveryoneAndAnIllegalOneOnlyToThoseWhoLearnedIt() {
		// R111
		val envoy = ActiveTipOff(1, TipOff.O8, 2, listOf("ada"), "ada")
		val slaves = ActiveTipOff(2, TipOff.O1, 2, listOf("ada", "kim"), "ada")

		assertThat(envoy.showsTakenTo("grace")).isTrue()
		assertThat(slaves.showsTakenTo("grace")).isFalse()
		assertThat(slaves.showsTakenTo("kim")).isTrue()
	}

	@Test
	fun shouldAnnounceEventsAndExpiredOpportunitiesWithoutNamingAnIllegalTaker() {
		// R109, creator's wording 2026-11-20
		assertThat(ActiveTipOff(1, TipOff.E10, 2, emptyList(), null).announcement(null)).startsWith("News: Epidemics")
		assertThat(ActiveTipOff(1, TipOff.O7, 2, emptyList(), null).announcement(null))
			.isEqualTo("News: the opportunity to buy drugs on Beta Hydri's Planet (15 HT) for sale on Gamma Leporis's Planet has expired.")
		assertThat(ActiveTipOff(1, TipOff.O7, 2, emptyList(), "ada").announcement("Vega Lines")).endsWith("has expired; it was taken.")
		assertThat(ActiveTipOff(1, TipOff.O8, 2, emptyList(), "ada").announcement("Vega Lines")).endsWith("has expired; Vega Lines took it.")
	}
}
