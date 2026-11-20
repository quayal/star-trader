package dev.adriangrzebyk.trader.game.news

/**
 * The creator's 14 events (R115), each announced in News when it takes place with its [headline]. A [oneTimeOnly] event
 * is retired once it has taken place; the rest go back to the pool (R109). What each does is [dev.adriangrzebyk.trader.game.Game]'s
 * to coordinate across the players and the board.
 */
enum class NewsEvent(val headline: String, val oneTimeOnly: Boolean) {
	PSIONIC_FIELD("Psionic field: every jump's chance is 4 lower this turn.", false),
	ALIEN_ATTACK("Aliens attack the frontier: every price falls by 3.", true),
	GALACTIC_WAR("Galactic war: CHIPS +3, POLY +6, ISO +2; no ships can be bought this turn and next; police +4 this turn.", false),
	MONEY_FRAUD("A perfect money fraud: every player's cash and loans are halved.", true),
	CIVIL_WAR(
		"Civil war in Gamma Leporis: weapons sell for triple there this turn; ships in its Port, its factories and warehouses are confiscated for half their value.",
		false
	),
	NEW_COLONY("A new colony at Mu Herculis: every price there rises by 5.", false),
	CHIPS_BREAKTHROUGH("A breakthrough in chips: CHIPS S/D +3 everywhere for the rest of the game.", true),
	SYNTHETIC_FOOD("Synthetic food: FOOD S/D +2 everywhere for the rest of the game.", true),
	ISO_BREAKTHROUGH("A breakthrough in isotopes: ISO S/D −3 everywhere for the rest of the game.", true),
	EPIDEMICS("Epidemics: every price rises by 4; Ports are closed this turn; police and security +1 this turn.", true),
	GOVERNMENT_SCREENING("Government screening: every player loses 2d6 reputation.", false),
	PIRATES(
		"Pirates attack Mu Herculis: its warehouses are emptied and ships in its Port flee or are lost; police and security +2 this turn.",
		false
	),
	SPECIAL_TAX("Special tax: 1 HT per warehouse block, 2 HT per factory unit and 5 HT per ship.", false),
	NEW_POLYMER_SYSTEM("A new polymer system: POLY −5, ISO and CHIPS +2 everywhere.", true)
}
