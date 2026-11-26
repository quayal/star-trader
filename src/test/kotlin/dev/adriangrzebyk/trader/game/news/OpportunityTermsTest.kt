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
	fun shouldGiveTheBlackMarketTableAndSayAnIllegalOneStaysSecret() {
		assertThat(TipOff.O1.opportunity!!.facts()).containsExactly(
			"Buy 1 to 10 units of slaves at 1 HT each, with a ship of yours on Mu Herculis's Planet.",
			"Each unit takes a passenger seat aboard, and can't be kept in a warehouse.",
			"Sell them on Epsilon Eridani's Planet, on the black market, in a later Opportunity: every unit aboard at once, " +
				"at 5, 8, 10, 15, 20 or 25 HT each for a d6 of 1 to 6.",
			"Illegal: nobody is told you took it."
		)
	}

	@Test
	fun shouldPriceEveryHullAndModuleOnOfferAndSayTheyAreMilitary() {
		val facts = TipOff.O20.opportunity!!.facts()

		assertThat(facts.first()).contains("Dagger 250 HT", "Sword 500 HT", "Spear 600 HT")
		assertThat(facts).anyMatch { it.startsWith("Add up to 5 military modules") && it.contains("Battle comm 50 HT") }
		assertThat(facts).contains("Illegal: nobody is told you took it.")
		assertThat(TipOff.O13.opportunity!!.facts()[1]).startsWith("Or skip the hull")
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
