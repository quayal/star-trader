package dev.adriangrzebyk.trader.game.news

/**
 * The creator's 14 events (R115), each announced in News when it takes place with its [headline]. A [oneTimeOnly] event
 * is retired once it has taken place; the rest go back to the pool (R109). What each does is [dev.adriangrzebyk.trader.game.Game]'s
 * to coordinate across the players and the board.
 */
/*
 * An S/D shift's sign (creator, 2026-11-21): a higher S/D means more supply, so prices tend to fall
 * ([dev.adriangrzebyk.trader.board.SupplyDemand] gives a positive indicator a negative price modifier); a lower one means
 * more demand. The headlines say which way the market moves.
 */
enum class NewsEvent(val headline: String, val oneTimeOnly: Boolean) {
	PSIONIC_FIELD(
		"Unknown radiation disrupts the psionic field that guides hyperjumps. Navigators report their instruments spinning: " +
			"every jump this turn will be much harder to make (jump chance −4 this turn).",
		false
	),
	ALIEN_ATTACK(
		"Alien raiders strike the frontier worlds and panic sweeps the exchanges. Traders dump their stock at any price " +
			"(every price −3).",
		true
	),
	GALACTIC_WAR(
		"War breaks out across the galaxy! The fleets scramble for chips, polymers and isotopes, and the navy requisitions " +
			"every shipyard (CHIPS +3, POLY +6, ISO +2; no ships can be bought this turn or next; police +4 this turn).",
		false
	),
	MONEY_FRAUD(
		"A flawless counterfeiting ring floods the galaxy with fake credits. The banks write off half of every account, and " +
			"half of every debt with it (every player's cash and loans are halved).",
		true
	),
	CIVIL_WAR(
		"Civil war tears Gamma Leporis apart. Rebels pay a fortune for guns, while the new regime seizes every foreign asset " +
			"within reach (weapons sell for triple there this turn; ships in its Port, factories and warehouses there are " +
			"confiscated for half their value).",
		false
	),
	NEW_COLONY(
		"Settlers found a new colony at Mu Herculis and will pay whatever it takes to get supplies in (every price there +5).",
		false
	),
	CHIPS_BREAKTHROUGH(
		"A breakthrough in chip fabrication: fully automated foundries come online across the galaxy, and chips are " +
			"suddenly everywhere (CHIPS supply up, prices tend to fall: S/D +3 everywhere for the rest of the game).",
		true
	),
	SYNTHETIC_FOOD(
		"Synthetic food vats go into mass production. Every colony now grows its own, and the food markets are flooded " +
			"(FOOD supply up, prices tend to fall: S/D +2 everywhere for the rest of the game).",
		true
	),
	ISO_BREAKTHROUGH(
		"A breakthrough in fusion drives: the new engines run on isotopes, every shipyard and power plant wants them, " +
			"and the refineries can't keep up (ISO demand up, prices tend to rise: S/D −3 everywhere for the rest of the game).",
		true
	),
	EPIDEMICS(
		"Epidemics sweep the inhabited worlds. Quarantine closes every spaceport and medicine eats into every budget " +
			"(every price +4; Ports are closed this turn: no jumps from or into a Port; police and security +1 this turn).",
		true
	),
	GOVERNMENT_SCREENING(
		"The government screens every corporation's books, and nobody comes out entirely clean (every player loses 2d6 " +
			"reputation).",
		false
	),
	PIRATES(
		"Pirates storm Mu Herculis! They loot its warehouses bare and open fire on the ships docked in its Port (every " +
			"warehouse there loses its goods; each ship in its Port escapes into Space on a d6 of 4–6 or is lost; police " +
			"and security +2 this turn).",
		false
	),
	SPECIAL_TAX(
		"The treasury is empty, and the government decrees a one-off levy on everything a corporation owns (1 HT per " +
			"warehouse block, 2 HT per factory unit, 5 HT per ship; a player who can't pay it all pays what they have, and the rest is waived).",
		false
	),
	NEW_POLYMER_SYSTEM(
		"A new polymer system replaces the old plastics overnight, and the electronics built around it are in demand " +
			"(POLY −5, ISO and CHIPS +2 everywhere).",
		true
	)
}
