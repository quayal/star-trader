package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.game.Module
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
	fun shouldWriteOutEverythingADeliveryInvolves() {
		// Creator, 2026-11-25: where from and to (Ports), what it needs, and the money.
		assertThat(TipOff.O22.opportunity!!.facts()).containsExactly(
			"Pick up exotic animals in Mu Herculis's Port and carry them to Epsilon Eridani's Port.",
			"Needs a ship of yours in Mu Herculis's Port with one whole Passenger module free; it stays taken until you deliver.",
			"Paid 150 HT when you deliver in Epsilon Eridani's Port on your Opportunity turn. No deadline, but the job is lost with the ship.",
			"Legal: everyone is told you took it."
		)
		assertThat(TipOff.O8.opportunity!!.facts()).contains("Needs reputation 20 or more to accept.")
		assertThat(TipOff.O9.opportunity!!.facts()[2]).startsWith("Paid 150 HT and +3 reputation when you deliver")
	}

	@Test
	fun shouldPointToTheGuidesBlackMarketTableAndSayAnIllegalOneStaysSecret() {
		// Creator, 2026-11-26: where to buy, the price, where to sell and what it takes aboard; the price table is in the Guide
		assertThat(TipOff.O1.opportunity!!.facts()).containsExactly(
			"Buy 1 to 10 units of slaves at 1 HT each, with a ship of yours on Mu Herculis's Planet.",
			"Each unit takes a passenger seat (Passenger module) aboard, and can't be kept in a warehouse.",
			"Sell them on Epsilon Eridani's Planet, on the black market, in a later Opportunity: every unit aboard at once, " +
				"at a d6 roll on the black market table in the Guide.",
			"Illegal: nobody is told you took it."
		)
	}

	@Test
	fun shouldOfferEveryMilitaryHullAndOrItsModulesOneOfEachKind() {
		// Creator, 2026-11-26: always "hull and/or modules", from one item to everything on offer
		val o13 = TipOff.O13.opportunity!!
		val o20 = TipOff.O20.opportunity!!

		assertThat(o13.description()).isEqualTo("buy a Spear hull and/or up to 4 military modules, one of each kind, on Beta Hydri's Planet")
		assertThat(TipOff.O16.opportunity!!.description()).isEqualTo("buy a Dagger hull and/or 1 military module, on Tau Ceti's Planet")
		assertThat(o20.facts()[1]).contains("Dagger 250 HT", "Sword 500 HT", "Spear 600 HT")
		assertThat(o20.facts()[2]).contains("Battle comm 50 HT")
		assertThat(o20.facts()).contains("Illegal: nobody is told you took it.")
		assertThat(o13.acceptsModulesAlone(Module.military())).isTrue()
		assertThat(o13.acceptsModulesAlone(listOf(Module.ARSENAL, Module.ARSENAL))).isFalse()
		assertThat(o13.acceptsModulesAlone(emptyList())).isFalse()
		assertThat(TipOff.O16.opportunity!!.acceptsModulesAlone(listOf(Module.ARSENAL, Module.DOGFIGHT))).isFalse()
		assertThat(TipOff.O4.opportunity!!.acceptsModulesAlone(listOf(Module.DOGFIGHT))).isFalse()
	}

	@Test
	fun shouldTellEveryOpportunityInTheCatalogueWhetherTakingItIsAnnounced() {
		TipOff.entries.mapNotNull { it.opportunity }.forEach { terms ->
			assertThat(
				terms.facts().last()
			).isEqualTo(if (terms.legal) "Legal: everyone is told you took it." else "Illegal: nobody is told you took it.")
		}
	}

	@Test
	fun shouldSellNoUnitsWhenItSellsNoGoods() {
		assertThat(OpportunityTerms.Expedition.maxUnits()).isNull()
		assertThat(OpportunityTerms.Expedition.specialGoods()).isNull()
	}
}
