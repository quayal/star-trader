/*
 * Pure helpers for the shared board (E03-S02): the Deck's map and fleet (s01) and System details (s02).
 * They read the game shape from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6) and return plain text
 * and data; index.html turns them into DOM with textContent only, since corporation names are player-typed.
 */

/**
 * D08: buy chances at or above this split are "moderate demand", below it "high demand". The brief leaves the
 * split open; 20% is the prototype's pick (S02-3), kept here as the one place to change it.
 */
export const MODERATE_DEMAND_MIN_CHANCE = 0.2;

/**
 * The five market levels of D08, from the demand end to the supply end (legend order). `step` picks the
 * colour token: the S/D steps of the design system, with "in balance" muted grey (no fifth colour yet).
 */
export const MARKET_LEVELS = [
	{ key: 'high-demand', label: 'high demand', arrow: '↑', hint: 'price likely to rise', step: 'bad' },
	{ key: 'moderate-demand', label: 'moderate demand', arrow: '↗', hint: 'price may rise', step: 'hard' },
	{ key: 'in-balance', label: 'in balance', arrow: '→', hint: 'price steady', step: 'balance' },
	{ key: 'moderate-supply', label: 'moderate supply', arrow: '↘', hint: 'price may fall', step: 'fair' },
	{ key: 'high-supply', label: 'high supply', arrow: '↓', hint: 'price likely to fall', step: 'easy' },
];

const LEVEL = Object.fromEntries(MARKET_LEVELS.map(level => [level.key, level]));

/** Cargo goods first, as the spec's example line reads: "POLY 4, ISO 3, CHIPS 12". */
const CARGO_ORDER = ['POLY', 'ISO', 'CHIPS', 'FOOD'];

export const LOCATIONS = [
	{ id: 'PORT', name: 'Port' },
	{ id: 'SPACE', name: 'Space' },
	{ id: 'PLANET', name: 'Planet' },
];

const LOCATION_NAMES = Object.fromEntries(LOCATIONS.map(location => [location.id, location.name]));

export const TIERS = [
	{ id: 'DICTATOR', name: 'Dictator' },
	{ id: 'WHOLESALER', name: 'Wholesaler' },
	{ id: 'RETAILER', name: 'Retailer' },
];

/**
 * The map is a flat-topped hexagon ring, rows 2 : 2 : 2 with the middle pair wider (D07):
 * Gamma, Beta / Sigma, Mu / Tau, Epsilon. Coordinates are in MAP_VIEW_BOX units.
 */
export const MAP_VIEW_BOX = '-20 0 800 670';
export const NODE_RADIUS = 64;
export const NODE_POSITIONS = {
	GAMMA_LEPORIS: [245, 101],
	BETA_HYDRI: [515, 101],
	SIGMA_DRACONIS: [110, 335],
	MU_HERCULIS: [650, 335],
	TAU_CETI: [245, 569],
	EPSILON_ERIDANI: [515, 569],
};

/** P(2d6 + indicator ≥ 0), as a fraction of the 36 outcomes. */
export function buyChance(indicator) {
	let ways = 0;
	for (let a = 1; a <= 6; a++) {
		for (let b = 1; b <= 6; b++) {
			if (a + b + indicator >= 0) {
				ways++;
			}
		}
	}
	return ways / 36;
}

/** D08: the market level of an S/D indicator, stepped by buy chance. */
export function marketLevel(indicator) {
	const chance = buyChance(indicator);
	if (chance >= 0.95) {
		return LEVEL['high-supply'];
	}
	if (chance >= 0.7) {
		return LEVEL['moderate-supply'];
	}
	if (chance >= 0.4) {
		return LEVEL['in-balance'];
	}
	return chance >= MODERATE_DEMAND_MIN_CHANCE ? LEVEL['moderate-demand'] : LEVEL['high-demand'];
}

/** "S/D −7", with a real minus sign and a plus for positive numbers. */
export function signedIndicator(indicator) {
	if (indicator > 0) {
		return `+${indicator}`;
	}
	return indicator < 0 ? `−${-indicator}` : '0';
}

/** Every pair of systems is connected: each edge once, as [fromId, toId]. */
export function mapEdges(systemIds) {
	const edges = [];
	for (let i = 0; i < systemIds.length; i++) {
		for (let j = i + 1; j < systemIds.length; j++) {
			edges.push([systemIds[i], systemIds[j]]);
		}
	}
	return edges;
}

/** "Vega Lines" → "VL": the first letter of each word, at most three. */
export function initials(corporationName) {
	const letters = String(corporationName ?? '').trim().split(/\s+/).filter(Boolean).map(word => word[0].toUpperCase());
	return letters.slice(0, 3).join('') || '?';
}

/** The game's ships at [systemId], yours first, each as { mine, name, label, corporationName, location }. */
export function shipsAt(game, systemId, myEmail) {
	const ships = [];
	for (const player of game.players) {
		for (const ship of player.ships) {
			if (ship.system === systemId) {
				const mine = player.email === myEmail;
				ships.push({
					mine,
					name: ship.name,
					label: mine ? `■ ${ship.name}` : `□ ${initials(player.corporationName)}`,
					corporationName: player.corporationName,
					location: ship.location,
				});
			}
		}
	}
	return [...ships.filter(ship => ship.mine), ...ships.filter(ship => !ship.mine)];
}

/** S02-4: the line under a system on the map. */
export function shipsUnderText(game, systemId, myEmail) {
	const ships = shipsAt(game, systemId, myEmail);
	return ships.length ? ships.map(ship => ship.label).join('  ') : 'no ships';
}

/** One good inside a map node: "POLY", "↓6" and its level. */
export function mapGood(entry) {
	const level = marketLevel(entry.indicator);
	return { goods: entry.goods, price: `${level.arrow}${entry.price}`, level };
}

/** S02-2: the arrow and label are in the accessible name, so colour is never the only cue. */
export function systemAccessibleName(game, system, myEmail) {
	const goods = system.market.map(entry => {
		const level = marketLevel(entry.indicator);
		return `${entry.goods} ${entry.price} HT, ${level.arrow} ${level.label}, ${level.hint}`;
	}).join('; ');
	const ships = shipsAt(game, system.id, myEmail);
	const mine = ships.filter(ship => ship.mine).length;
	const others = ships.length - mine;
	return `${system.name}: ${goods}. ${mine} of your ships, ${others} ${others === 1 ? 'other ship' : 'other ships'}. Open system details.`;
}

/** "POLY 4, ISO 3, CHIPS 12", or "Empty" for a ship carrying nothing. */
export function cargoText(cargo) {
	const carried = Object.entries(cargo ?? {}).filter(([, units]) => units > 0);
	if (!carried.length) {
		return 'Empty';
	}
	const rank = goods => (CARGO_ORDER.includes(goods) ? CARGO_ORDER.indexOf(goods) : CARGO_ORDER.length);
	return carried.sort(([a], [b]) => rank(a) - rank(b)).map(([goods, units]) => `${goods} ${units}`).join(', ');
}

/** "cargo 7 / 10 used" (R42). */
export function cargoUsedText(ship) {
	return `cargo ${ship.cargoUsed} / ${ship.cargoSpaces} used`;
}

/**
 * S02-5: each of your ships with where it is and what it carries. Position comes from the public players[],
 * cargo from `you.ships` (the only place it is sent).
 */
export function fleet(game, myEmail) {
	const me = game.players.find(player => player.email === myEmail);
	const cargoByName = Object.fromEntries((game.you?.ships ?? []).map(ship => [ship.name, ship]));
	return (me?.ships ?? []).map(ship => {
		const own = cargoByName[ship.name] ?? { cargo: {}, cargoUsed: 0, cargoSpaces: 10 };
		return {
			name: ship.name,
			systemId: ship.system,
			systemName: ship.systemName,
			location: LOCATION_NAMES[ship.location] ?? ship.location,
			cargo: cargoText(own.cargo),
			cargoUsed: cargoUsedText(own),
		};
	});
}

/** "Wayfarer — Tau Ceti · Port — POLY 4, ISO 3, CHIPS 12 · cargo 7 / 10 used". */
export function fleetLine(entry) {
	return `${entry.name} — ${entry.systemName} · ${entry.location} — ${entry.cargo} · ${entry.cargoUsed}`;
}

/** R31: the statistics line of a location. */
export function locationStats(system, locationId) {
	if (locationId === 'PORT') {
		return `Class ${system.portClass} · Legal system ${system.legalSystem}`;
	}
	return locationId === 'SPACE' ? `Police ${system.police}` : `Security ${system.security}`;
}

/**
 * S02-7: the three locations of [system], each with its ships. Your ships carry their cargo line; another
 * player's ship shows its owner only (their cargo is never sent, let alone shown).
 */
export function locations(game, system, myEmail) {
	const ships = shipsAt(game, system.id, myEmail);
	const yours = Object.fromEntries(fleet(game, myEmail).map(entry => [entry.name, entry]));
	return LOCATIONS.map(location => ({
		id: location.id,
		name: location.name,
		stats: locationStats(system, location.id),
		ships: ships.filter(ship => ship.location === location.id).map(ship => ({
			mine: ship.mine,
			text: ship.mine
				? `■ ${ship.name} — you · ${yours[ship.name].cargo} · ${yours[ship.name].cargoUsed}`
				: `□ ${ship.name} — ${ship.corporationName}`,
		})),
	}));
}

/** S02-7/8: per good at [system], the corporations holding each tier; players without a position are left out. */
export function marketPositions(game, system) {
	return system.market.map(entry => {
		const holders = tier => game.players
			.filter(player => (player.marketPositions ?? []).some(position =>
				position.system === system.id && position.goods === entry.goods && position.tier === tier))
			.map(player => player.corporationName);
		const tiers = TIERS.map(tier => ({ id: tier.id, name: tier.name, holders: holders(tier.id) }));
		return { goods: entry.goods, tiers, nobody: tiers.every(tier => tier.holders.length === 0) };
	});
}

/** S02-12: the notice over a stale board. */
export function staleText(secondsAgo) {
	const seconds = Math.max(0, Math.round(secondsAgo));
	return `Couldn't refresh the game. Showing the state from ${seconds} ${seconds === 1 ? 'second' : 'seconds'} ago.`;
}
