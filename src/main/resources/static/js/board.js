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
 * The five market levels of D08, from the demand end to the supply end (legend order). The triangle gives the
 * direction (▲ the price may rise, ▼ it may fall; in balance has none) and the colour how likely it is: green up,
 * red down, bright for high, muted for moderate (creator, 2026-11-02). `key` is the `data-level` that
 * `.market-level` colours.
 */
export const MARKET_LEVELS = [
	{ key: 'high-demand', label: 'high demand', symbol: '▲', hint: 'price likely to rise' },
	{ key: 'moderate-demand', label: 'moderate demand', symbol: '▲', hint: 'price may rise' },
	{ key: 'in-balance', label: 'in balance', symbol: '', hint: 'price steady' },
	{ key: 'moderate-supply', label: 'moderate supply', symbol: '▼', hint: 'price may fall' },
	{ key: 'high-supply', label: 'high supply', symbol: '▼', hint: 'price likely to fall' },
];

/** A no-break space between triangle and price, so the two never wrap apart. */
const SYMBOL_GAP = '\u00a0';

const LEVEL = Object.fromEntries(MARKET_LEVELS.map(level => [level.key, level]));

/** Cargo goods first, as the spec's example line reads: "POLY 4, ISO 3, CHIPS 12". */
const CARGO_ORDER = ['POLY', 'ISO', 'CHIPS', 'FOOD'];

export const LOCATIONS = [
	{ id: 'PORT', name: 'Port' },
	{ id: 'SPACE', name: 'Space' },
	{ id: 'PLANET', name: 'Planet' },
];

/** R60: a ship bought at a shipyard waits there; only Beta Hydri, Epsilon Eridani and Tau Ceti have one. */
export const SHIPYARD = { id: 'SHIPYARD', name: 'Shipyard' };

const LOCATION_NAMES = Object.fromEntries([...LOCATIONS, SHIPYARD].map(location => [location.id, location.name]));

export const TIERS = [
	{ id: 'DICTATOR', name: 'Dictator' },
	{ id: 'WHOLESALER', name: 'Wholesaler' },
	{ id: 'RETAILER', name: 'Retailer' },
];

/**
 * The map is a flat-topped hexagon ring, rows 2 : 2 : 2 with the middle pair wider (D07):
 * Gamma, Beta / Epsilon, Sigma / Tau, Mu. Coordinates are in map units.
 */
export const NODE_RADIUS = 64;
export const NODE_POSITIONS = {
	GAMMA_LEPORIS: [245, 101],
	BETA_HYDRI: [515, 101],
	EPSILON_ERIDANI: [110, 335],
	SIGMA_DRACONIS: [650, 335],
	TAU_CETI: [245, 569],
	MU_HERCULIS: [515, 569],
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

// R68: an arrowhead starts this far outside a system's circle and reaches this far out, this wide at its base.
const ARROW_BASE = 4;
const ARROW_TIP = 34;
const ARROW_HALF_WIDTH = 14;

/** How many of the highest, and of the lowest, different base jump chances on the board get their own colour. */
const JUMP_LEVEL_SPAN = 3;

/**
 * R68: how a base jump chance compares with the others on the map, from the different values in [chances]: `high` for the
 * three highest, `low` for the three lowest, `mid` for the rest (high wins if a short list makes them overlap).
 */
export function jumpChanceLevel(chances, chance) {
	const values = [...new Set(chances)].sort((a, b) => a - b);
	if (values.slice(-JUMP_LEVEL_SPAN).includes(chance)) {
		return 'high';
	}
	return values.slice(0, JUMP_LEVEL_SPAN).includes(chance) ? 'low' : 'mid';
}

/**
 * R68: one arrowhead per system and route, just outside the system's circle and pointing at the other system, holding the
 * base chance of a jump that way (`board[].jumpChances`) and its [jumpChanceLevel] among all of them. [points] is the SVG
 * polygon and [x], [y] the number's centre (the triangle's centroid); coordinates are in map units, rounded to a tenth.
 */
export function jumpArrows(systems) {
	const round = value => Math.round(value * 10) / 10;
	const arrows = [];
	for (const system of systems) {
		const from = NODE_POSITIONS[system.id];
		for (const [to, chance] of Object.entries(system.jumpChances ?? {})) {
			const target = NODE_POSITIONS[to];
			if (!from || !target) {
				continue;
			}
			const length = Math.hypot(target[0] - from[0], target[1] - from[1]);
			const [ux, uy] = [(target[0] - from[0]) / length, (target[1] - from[1]) / length];
			const along = distance => [from[0] + ux * distance, from[1] + uy * distance];
			const [bx, by] = along(NODE_RADIUS + ARROW_BASE);
			const [tx, ty] = along(NODE_RADIUS + ARROW_TIP);
			const corners = [
				[bx - uy * ARROW_HALF_WIDTH, by + ux * ARROW_HALF_WIDTH],
				[bx + uy * ARROW_HALF_WIDTH, by - ux * ARROW_HALF_WIDTH],
				[tx, ty],
			];
			const [cx, cy] = along(NODE_RADIUS + (2 * ARROW_BASE + ARROW_TIP) / 3);
			arrows.push({
				from: system.id,
				to,
				chance,
				points: corners.map(([x, y]) => `${round(x)},${round(y)}`).join(' '),
				x: round(cx),
				y: round(cy),
			});
		}
	}
	const chances = arrows.map(arrow => arrow.chance);
	return arrows.map(arrow => ({ ...arrow, level: jumpChanceLevel(chances, arrow.chance) }));
}

/** R68: "Jump chances from here: Beta Hydri 7, …" for a system's accessible name, in board order; "" with none. */
export function jumpChancesText(board, system) {
	const chances = board.filter(other => system.jumpChances?.[other.id] !== undefined).map(other => `${other.name} ${system.jumpChances[other.id]}`);
	return chances.length ? `Base jump chances from here: ${chances.join(', ')}.` : '';
}

const NAME_GAP = 12; // between a circle and its name's baseline above it
const NAME_HEIGHT = 20; // the name's font size
const SHIP_ROW = 20; // between two lines of ship labels
const SHIP_GAP = 24; // between a circle or a name and the ship line next to it

/**
 * R68: where a system's name and ship lines go (baselines, in map units), clear of its jump arrowheads, which
 * leave the top row sideways and down, the bottom row sideways and up, and the middle row on every side but straight up
 * and down. So the top row stacks its ships above its name, the bottom row puts both under its circle, and the middle
 * row keeps its name above and its ships below, a little further out. [rows] is how many ship lines it has.
 */
export function nodeLabels(systemId, rows) {
	const [, y] = NODE_POSITIONS[systemId];
	const lines = count => Array.from({ length: count }, (_, index) => index);
	if (systemId === 'GAMMA_LEPORIS' || systemId === 'BETA_HYDRI') {
		const name = y - NODE_RADIUS - NAME_GAP;
		const lowest = name - NAME_HEIGHT - SHIP_GAP / 2;
		return { name, ships: lines(rows).map(index => lowest - (rows - 1 - index) * SHIP_ROW) };
	}
	if (systemId === 'TAU_CETI' || systemId === 'MU_HERCULIS') {
		const name = y + NODE_RADIUS + NAME_GAP + NAME_HEIGHT;
		return { name, ships: lines(rows).map(index => name + SHIP_GAP + index * SHIP_ROW) };
	}
	return {
		name: y - NODE_RADIUS - NAME_GAP - 16,
		ships: lines(rows).map(index => y + NODE_RADIUS + SHIP_GAP + 16 + index * SHIP_ROW),
	};
}

/**
 * The map's viewBox: x from -20, 800 wide; y from 0 to 670 for the circles, grown up or down to hold every label
 * [nodeLabels] placed.
 */
export function mapViewBox(labels) {
	const baselines = labels.flatMap(label => [label.name, ...label.ships]);
	const top = Math.min(0, Math.min(...baselines) - NAME_HEIGHT - 4);
	const bottom = Math.max(670, Math.max(...baselines) + 10);
	return `-20 ${top} 800 ${bottom - top}`;
}

/** "Vega Lines" → "VL": the first letter of each word, at most three. */
export function initials(corporationName) {
	const letters = String(corporationName ?? '').trim().split(/\s+/).filter(Boolean).map(word => word[0].toUpperCase());
	return letters.slice(0, 3).join('') || '?';
}

/** The game's ships at [systemId], yours first, each as { mine, name, label, corporationName, location }. */
export function shipsAt(game, systemId, myId) {
	const ships = [];
	for (const player of game.players) {
		for (const ship of player.ships) {
			if (ship.system === systemId) {
				const mine = player.id === myId;
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
export function shipsUnderText(game, systemId, myId) {
	const ships = shipsAt(game, systemId, myId);
	return ships.length ? ships.map(ship => ship.label).join('  ') : 'no ships';
}

/** One good inside a map node: "POLY", the price ("6\u00a0" when a triangle follows), its level's triangle ("" in balance) and the level. */
export function mapGood(entry) {
	const level = marketLevel(entry.indicator);
	return { goods: entry.goods, price: level.symbol ? `${entry.price}${SYMBOL_GAP}` : `${entry.price}`, symbol: level.symbol, level };
}

/** R60: what a shipyard on the map means, in the map key and a system's accessible name. */
export const SHIPYARD_TEXT = 'Shipyard: build and repair ships here.';

/** S02-2: the label and hint are in the accessible name (words, not symbols a screen reader would spell out). */
export function systemAccessibleName(game, system, myId) {
	const goods = system.market.map(entry => {
		const level = marketLevel(entry.indicator);
		return `${entry.goods} ${entry.price} HT, ${level.label}, ${level.hint}`;
	}).join('; ');
	const ships = shipsAt(game, system.id, myId);
	const mine = ships.filter(ship => ship.mine).length;
	const others = ships.length - mine;
	const jumps = jumpChancesText(game.board ?? [], system);
	const shipyard = system.shipyard ? ` ${SHIPYARD_TEXT}` : '';
	return `${system.name}: ${goods}.${shipyard} ${mine} of your ships, ${others} ${others === 1 ? 'other ship' : 'other ships'}.${jumps ? ` ${jumps}` : ''} Open system details.`;
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
export function fleet(game, myId) {
	const me = game.players.find(player => player.id === myId);
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
	if (locationId === SHIPYARD.id) {
		return 'New ships wait here a turn';
	}
	if (locationId === 'PORT') {
		return `Class ${system.portClass} · Legal system ${system.legalSystem}`;
	}
	return locationId === 'SPACE' ? `Police ${system.police}` : `Security ${system.security}`;
}

/**
 * S02-7: the three locations of [system], each with its ships. Your ships carry their cargo line; another
 * player's ship shows its owner only (their cargo is never sent, let alone shown).
 */
export function locations(game, system, myId) {
	const ships = shipsAt(game, system.id, myId);
	const yours = Object.fromEntries(fleet(game, myId).map(entry => [entry.name, entry]));
	// R60: the shipyard is listed only while a ship waits in it.
	const shown = ships.some(ship => ship.location === SHIPYARD.id) ? [...LOCATIONS, SHIPYARD] : LOCATIONS;
	return shown.map(location => ({
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

/**
 * E06-S02-2, S02-3: what is built at [system], public (R50): every corporation's warehouse there (blocks and cargo
 * spaces, R45) and factory there (good and units a turn, R46), and the goods the system can produce (R46), none at
 * some systems. Older responses have no warehouses, factories or producible goods; they read as none.
 */
export function systemHoldings(game, system) {
	const at = key => game.players.flatMap(player => (player[key] ?? [])
		.filter(item => item.system === system.id)
		.map(item => ({ ...item, corporationName: player.corporationName })));
	return {
		producible: system.producibleGoods ?? [],
		warehouses: at('warehouses'),
		factories: at('factories'),
	};
}

/** S02-12: the notice over a stale board is this sentence, announced once, then the age, which isn't. */
export const STALE_MESSAGE = "Couldn't refresh the game.";

export function staleAgeText(secondsAgo) {
	const seconds = Math.max(0, Math.round(secondsAgo));
	return `Showing the state from ${seconds} ${seconds === 1 ? 'second' : 'seconds'} ago.`;
}

export function staleText(secondsAgo) {
	return `${STALE_MESSAGE} ${staleAgeText(secondsAgo)}`;
}
