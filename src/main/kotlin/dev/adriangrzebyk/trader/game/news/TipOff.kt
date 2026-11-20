package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.EPSILON_ERIDANI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.SIGMA_DRACONIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.news.OpportunityTerms.Delivery
import dev.adriangrzebyk.trader.game.news.OpportunityTerms.Expedition
import dev.adriangrzebyk.trader.game.news.OpportunityTerms.Furs
import dev.adriangrzebyk.trader.game.news.OpportunityTerms.IllegalGoods
import dev.adriangrzebyk.trader.game.news.OpportunityTerms.MilitaryModules
import dev.adriangrzebyk.trader.game.news.OpportunityTerms.MilitaryShip
import dev.adriangrzebyk.trader.game.news.SpecialGoods.DRUGS
import dev.adriangrzebyk.trader.game.news.SpecialGoods.SLAVES
import dev.adriangrzebyk.trader.game.news.SpecialGoods.WEAPONS
import dev.adriangrzebyk.trader.game.news.TipOffNeed.Companion.buss
import dev.adriangrzebyk.trader.game.news.TipOffNeed.Companion.crim
import dev.adriangrzebyk.trader.game.news.TipOffNeed.Companion.pol

/**
 * The creator's 40 tip-offs (vault note `Intel.md`, R109): opportunities O1–O26 and events E1–E14. A tip-off is only
 * information about something coming (creator, 2026-11-20): learned for [need], it becomes effective [lead] turns after
 * its draw, when its [event] takes place or its [opportunity] expires.
 */
enum class TipOff(val need: TipOffNeed, val lead: Int, val opportunity: OpportunityTerms?, val event: NewsEvent?) {
	O1(crim(4), 3, IllegalGoods(SLAVES, 1, MU_HERCULIS, EPSILON_ERIDANI)),
	O2(crim(8), 2, IllegalGoods(WEAPONS, 3, EPSILON_ERIDANI, MU_HERCULIS)),
	O3(crim(1), 1, IllegalGoods(WEAPONS, 10, TAU_CETI, GAMMA_LEPORIS)),
	O4(crim(1), 1, MilitaryModules(listOf(Module.BATTLE_COMM), GAMMA_LEPORIS)),
	O5(buss(2), 2, Furs(10, MU_HERCULIS, 30, BETA_HYDRI)),
	O6(crim(5), 2, IllegalGoods(SLAVES, 2, MU_HERCULIS, BETA_HYDRI)),
	O7(crim(6), 1, IllegalGoods(DRUGS, 15, BETA_HYDRI, GAMMA_LEPORIS)),
	O8(pol(5), 1, Delivery("an envoy and his team", EPSILON_ERIDANI, MU_HERCULIS, Module.PASSENGER, 20, 200, 0)),
	O9(buss(10), 2, Delivery("exotic plants", GAMMA_LEPORIS, EPSILON_ERIDANI, Module.CARGO, 0, 150, 3)),
	O10(pol(10), 1, IllegalGoods(SLAVES, 1, MU_HERCULIS, GAMMA_LEPORIS)),
	O11(crim(3), 3, IllegalGoods(SLAVES, 4, MU_HERCULIS, GAMMA_LEPORIS)),
	O12(crim(6), 1, IllegalGoods(DRUGS, 12, EPSILON_ERIDANI, GAMMA_LEPORIS)),
	O13(crim(10), 1, MilitaryShip(listOf(Hull.SPEAR), 5, BETA_HYDRI, hullOptional = true)),
	O14(pol(8), 4, MilitaryModules(Module.military(), TAU_CETI)),
	O15(pol(2), 2, Expedition),
	O16(buss(3), 4, MilitaryShip(listOf(Hull.DAGGER), 1, TAU_CETI, hullOptional = false)),
	O17(crim(8), 3, IllegalGoods(WEAPONS, 8, EPSILON_ERIDANI, GAMMA_LEPORIS)),
	O18(buss(2), 2, IllegalGoods(WEAPONS, 3, BETA_HYDRI, GAMMA_LEPORIS)),
	O19(crim(8), 2, IllegalGoods(DRUGS, 10, TAU_CETI, SIGMA_DRACONIS)),
	O20(buss(10), 2, MilitaryShip(Hull.military(), 5, EPSILON_ERIDANI, hullOptional = false)),
	O21(crim(8), 2, MilitaryShip(listOf(Hull.SWORD), 3, MU_HERCULIS, hullOptional = false)),
	O22(buss(5), 1, Delivery("exotic animals", MU_HERCULIS, EPSILON_ERIDANI, Module.PASSENGER, 0, 150, 0)),
	O23(crim(5), 2, IllegalGoods(WEAPONS, 3, EPSILON_ERIDANI, MU_HERCULIS)),
	O24(crim(4), 3, MilitaryShip(listOf(Hull.DAGGER), 1, SIGMA_DRACONIS, hullOptional = false)),
	O25(pol(7), 1, IllegalGoods(WEAPONS, 5, BETA_HYDRI, MU_HERCULIS)),
	O26(pol(5), 1, MilitaryModules(Module.military(), EPSILON_ERIDANI)),
	E1(buss(5), 1, NewsEvent.PSIONIC_FIELD),
	E2(pol(10), 1, NewsEvent.ALIEN_ATTACK),
	E3(pol(3), 4, NewsEvent.GALACTIC_WAR),
	E4(crim(3), 3, NewsEvent.MONEY_FRAUD),
	E5(pol(2), 2, NewsEvent.CIVIL_WAR),
	E6(pol(3), 4, NewsEvent.NEW_COLONY),
	E7(buss(6), 2, NewsEvent.CHIPS_BREAKTHROUGH),
	E8(buss(8), 3, NewsEvent.SYNTHETIC_FOOD),
	E9(buss(6), 2, NewsEvent.ISO_BREAKTHROUGH),
	E10(pol(7), 1, NewsEvent.EPIDEMICS),
	E11(pol(8), 4, NewsEvent.GOVERNMENT_SCREENING),
	E12(crim(10), 1, NewsEvent.PIRATES),
	E13(pol(3), 4, NewsEvent.SPECIAL_TAX),
	E14(buss(3), 4, NewsEvent.NEW_POLYMER_SYSTEM);

	constructor(need: TipOffNeed, lead: Int, opportunity: OpportunityTerms) : this(need, lead, opportunity, null)

	constructor(need: TipOffNeed, lead: Int, event: NewsEvent) : this(need, lead, null, event)

	/** Whether this tip-off goes back to the pool once effective: all but a one-time-only event (R109). */
	fun returnsToPool(): Boolean = event?.oneTimeOnly != true

	/** OPPORTUNITY or EVENT: what kind of tip-off it is, shown only once learned (R109). */
	fun kindName(): String = if (opportunity != null) "OPPORTUNITY" else "EVENT"

	/** The details a player sees once they've learned it (R110): "Opportunity: …" or "Event: …". */
	fun details(): String = opportunity?.let { "Opportunity: ${it.description()}." } ?: "Event: ${checkNotNull(event).headline}"
}
