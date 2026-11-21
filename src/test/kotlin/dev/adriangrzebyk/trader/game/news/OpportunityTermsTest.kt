package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** What a goods opportunity tells its form (playtest, 2026-11-21): how many units, and what each takes aboard (R112). */
class OpportunityTermsTest {

	@Test
	fun shouldSellUpToTenFursThatNeedNoCargoSpace() {
		val furs = OpportunityTerms.Furs(10, MU_HERCULIS, 30, BETA_HYDRI)

		assertThat(furs.maxUnits()).isEqualTo(10)
		assertThat(furs.specialGoods().hold).isEqualTo(SpecialGoods.Hold.NONE)
	}

	@Test
	fun shouldSayWhatEachIllegalUnitTakesAboard() {
		val weapons = OpportunityTerms.IllegalGoods(SpecialGoods.WEAPONS, 15, MU_HERCULIS, BETA_HYDRI)

		assertThat(weapons.maxUnits()).isEqualTo(10)
		assertThat(weapons.specialGoods().hold).isEqualTo(SpecialGoods.Hold.CARGO_SPACE)
	}

	@Test
	fun shouldSellNoUnitsWhenItSellsNoGoods() {
		assertThat(OpportunityTerms.Expedition.maxUnits()).isNull()
		assertThat(OpportunityTerms.Expedition.specialGoods()).isNull()
	}
}
