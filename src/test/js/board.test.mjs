import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	MARKET_LEVELS,
	MODERATE_DEMAND_MIN_CHANCE,
	NODE_POSITIONS,
	buyChance,
	cargoText,
	fleet,
	fleetLine,
	initials,
	locations,
	mapEdges,
	mapGood,
	marketLevel,
	marketPositions,
	shipsUnderText,
	signedIndicator,
	STALE_MESSAGE,
	staleAgeText,
	staleText,
	HANGAR_TEXT,
	SHIPYARD_TEXT,
	systemAccessibleName,
	jumpArrows,
	jumpChanceLevel,
	jumpChancesText,
	mapViewBox,
	nodeLabels,
	systemHoldings,
} from '../../main/resources/static/js/board.js';

const tauCeti = {
	id: 'TAU_CETI',
	name: 'Tau Ceti',
	portClass: 4,
	legalSystem: 2,
	police: 3,
	security: 4,
	market: [{ goods: 'ISO', price: 4, indicator: -9 }, { goods: 'POLY', price: 6, indicator: -10 }],
};

const game = {
	id: 'g-1',
	status: 'RUNNING',
	players: [
		{
			id: 'ada',
			corporationName: 'Orion Freight',
			ships: [
				{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' },
				{ name: 'Lantern', system: 'BETA_HYDRI', systemName: 'Beta Hydri', location: 'SPACE' },
			],
			marketPositions: [{ system: 'TAU_CETI', goods: 'POLY', tier: 'WHOLESALER' }],
		},
		{
			id: 'grace',
			corporationName: 'Vega Lines',
			ships: [
				{ name: 'Meridian', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PLANET' },
				{ name: 'Halcyon', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', location: 'PORT' },
			],
			marketPositions: [{ system: 'TAU_CETI', goods: 'POLY', tier: 'DICTATOR' }],
		},
	],
	board: [tauCeti],
	you: {
		money: 300,
		ships: [
			{ name: 'Wayfarer', cargo: { CHIPS: 12, ISO: 3, POLY: 4 }, cargoUsed: 7, cargoSpaces: 10 },
			{ name: 'Lantern', cargo: {}, cargoUsed: 0, cargoSpaces: 10 },
		],
	},
};

describe('buyChance', () => {
	it('counts the 2d6 outcomes where the roll plus the indicator is 0 or more', () => {
		assert.equal(buyChance(1), 1);
		assert.equal(buyChance(-7), 21 / 36);
		assert.equal(buyChance(-12), 1 / 36);
		assert.equal(buyChance(-13), 0);
	});
});

describe('marketLevel (D08)', () => {
	it('steps buy chance into the five market levels', () => {
		assert.equal(marketLevel(-3).label, 'high supply'); // 35/36 ≈ 97%
		assert.equal(marketLevel(-5).label, 'moderate supply'); // 30/36 ≈ 83%
		assert.equal(marketLevel(-4).label, 'moderate supply'); // 33/36 ≈ 92%
		assert.equal(marketLevel(-7).label, 'in balance'); // 21/36 ≈ 58%
		assert.equal(marketLevel(-8).label, 'in balance'); // 15/36 ≈ 42%
		assert.equal(marketLevel(-9).label, 'moderate demand'); // 10/36 ≈ 28%
		assert.equal(marketLevel(-10).label, 'high demand'); // 6/36 ≈ 17%
	});

	it('splits moderate from high demand at the setting, 20% by default', () => {
		assert.equal(MODERATE_DEMAND_MIN_CHANCE, 0.2);
		assert.ok(buyChance(-9) >= MODERATE_DEMAND_MIN_CHANCE && buyChance(-10) < MODERATE_DEMAND_MIN_CHANCE);
	});

	it('shows direction with a triangle (none in balance) and leaves likelihood to colour and words', () => {
		assert.deepEqual(MARKET_LEVELS.map(level => level.symbol), ['▲', '▲', '', '▼', '▼']);
		assert.ok(MARKET_LEVELS.every(level => level.hint && level.key));
	});
});

describe('signedIndicator', () => {
	it('shows a plus for positive numbers and a real minus for negative ones', () => {
		assert.equal(signedIndicator(1), '+1');
		assert.equal(signedIndicator(0), '0');
		assert.equal(signedIndicator(-7), '−7');
	});
});

describe('the map', () => {
	it('places all six systems as a flat-topped hexagon ring, in rows of two', () => {
		assert.equal(Object.keys(NODE_POSITIONS).length, 6);
		assert.equal(NODE_POSITIONS.GAMMA_LEPORIS[1], NODE_POSITIONS.BETA_HYDRI[1]);
		assert.equal(NODE_POSITIONS.EPSILON_ERIDANI[1], NODE_POSITIONS.SIGMA_DRACONIS[1]);
		assert.equal(NODE_POSITIONS.TAU_CETI[1], NODE_POSITIONS.MU_HERCULIS[1]);
		assert.ok(NODE_POSITIONS.EPSILON_ERIDANI[0] < NODE_POSITIONS.GAMMA_LEPORIS[0]);
		assert.ok(NODE_POSITIONS.SIGMA_DRACONIS[0] > NODE_POSITIONS.BETA_HYDRI[0]);
	});

	it('connects every pair of systems once', () => {
		const edges = mapEdges(Object.keys(NODE_POSITIONS));
		assert.equal(edges.length, 15);
		assert.equal(new Set(edges.map(edge => edge.join('-'))).size, 15);
	});

	it('lists a good with its price then its triangle, and a steady price on its own', () => {
		const good = mapGood({ goods: 'POLY', price: 6, indicator: -10 });
		assert.equal(good.goods, 'POLY');
		assert.equal(good.symbol, '▲');
		assert.equal(good.price, '6\u00a0');
		assert.equal(good.level.key, 'high-demand');
		assert.equal(mapGood({ goods: 'ISO', price: 10, indicator: -7 }).symbol, '');
		assert.equal(mapGood({ goods: 'ISO', price: 10, indicator: -7 }).price, '10');
	});

	it('puts every good, its price and its market label in words in the accessible name', () => {
		assert.equal(
			systemAccessibleName(game, tauCeti, 'ada'),
			'Tau Ceti: ISO 4 HT, moderate demand, price may rise; POLY 6 HT, high demand, price likely to rise. '
				+ '1 of your ships, 1 other ship. Open system details.',
		);
	});

	it('says a system has a shipyard (R60)', () => {
		assert.equal(SHIPYARD_TEXT, 'Shipyard: build and repair ships here.');
		assert.match(systemAccessibleName(game, { ...tauCeti, shipyard: true }, 'ada'), /price likely to rise\. Shipyard: build and repair ships here\. 1 of your ships/);
	});
});

describe('the Parking Hangar (R127)', () => {
	it('says a system has one in its accessible name, after the shipyard', () => {
		assert.equal(HANGAR_TEXT, 'Parking Hangar: protects a ship from sabotage.');
		assert.match(
			systemAccessibleName(game, { ...tauCeti, shipyard: true, hangar: true }, 'ada'),
			/Shipyard: build and repair ships here\. Parking Hangar: protects a ship from sabotage\. 1 of your ships/,
		);
	});

	it('marks a parked ship in the system details', () => {
		const parked = { ...game, players: game.players.map(p => ({ ...p, ships: p.ships.map(s => ({ ...s, hangar: s.location === 'PORT' })) })) };
		const port = locations(parked, tauCeti, 'ada').find(location => location.id === 'PORT');

		assert.ok(port.ships.length > 0);
		assert.ok(port.ships.every(ship => ship.text.endsWith(' · parked')));
		assert.ok(locations(game, tauCeti, 'ada').flatMap(location => location.ships).every(ship => !ship.text.includes('parked')));
	});
});

describe('ships under a system (S02-4)', () => {
	it('shows your ships by name first and other players by corporation initials', () => {
		assert.equal(shipsUnderText(game, 'TAU_CETI', 'ada'), '■ Wayfarer  □ VL');
		assert.equal(shipsUnderText(game, 'TAU_CETI', 'grace'), '■ Meridian  □ OF');
	});

	it('says "no ships" for an empty system', () => {
		assert.equal(shipsUnderText(game, 'MU_HERCULIS', 'ada'), 'no ships');
	});

	it('takes up to three initials', () => {
		assert.equal(initials('Vega Lines'), 'VL');
		assert.equal(initials('  altair trade co  '), 'ATC');
		assert.equal(initials('The Big Space Company'), 'TBS');
		assert.equal(initials('Kessler'), 'K');
	});
});

describe('Your fleet (S02-5)', () => {
	it('lists each of your ships with system, location, cargo and cargo space used', () => {
		assert.deepEqual(fleet(game, 'ada').map(fleetLine), [
			'Wayfarer — Tau Ceti · Port — POLY 4, ISO 3, CHIPS 12 · cargo 7 / 10 used',
			'Lantern — Beta Hydri · Space — Empty · cargo 0 / 10 used',
		]);
	});

	it('links each entry to its system', () => {
		assert.deepEqual(fleet(game, 'ada').map(entry => entry.systemId), ['TAU_CETI', 'BETA_HYDRI']);
	});

	it('leaves out goods with no units', () => {
		assert.equal(cargoText({ FOOD: 0, ISO: 2 }), 'ISO 2');
		assert.equal(cargoText({}), 'Empty');
	});
});

describe('System details locations (S02-7)', () => {
	it('gives each location its statistics and its ships', () => {
		const [port, space, planet] = locations(game, tauCeti, 'ada');
		assert.deepEqual([port.name, port.stats], ['Port', 'Class 4 · Legal system 2']);
		assert.deepEqual([space.name, space.stats], ['Space', 'Police 3']);
		assert.deepEqual([planet.name, planet.stats], ['Planet', 'Security 4']);
		assert.deepEqual(port.ships.map(ship => ship.text), ['■ Wayfarer — you · POLY 4, ISO 3, CHIPS 12 · cargo 7 / 10 used']);
		assert.deepEqual(space.ships, []);
	});

	it("names another player's ship and its owner, never its cargo", () => {
		const planet = locations(game, tauCeti, 'ada')[2];
		assert.deepEqual(planet.ships.map(ship => ship.text), ['□ Meridian — Vega Lines']);
	});
});

describe('Market positions (S02-7, S02-8)', () => {
	it('lists the holders of each tier per good, leaving out players without a position', () => {
		const [iso, poly] = marketPositions(game, tauCeti);
		assert.equal(iso.goods, 'ISO');
		assert.equal(iso.nobody, true);
		assert.equal(poly.nobody, false);
		assert.deepEqual(poly.tiers.map(tier => [tier.name, tier.holders]), [
			['Dictator', ['Vega Lines']],
			['Wholesaler', ['Orion Freight']],
			['Retailer', []],
		]);
	});
});

describe('Warehouses and factories (E06-S02)', () => {
	const built = {
		...game,
		players: [
			{ ...game.players[0], warehouses: [{ system: 'TAU_CETI', blocks: 2, cargoSpaces: 20 }, { system: 'MU_HERCULIS', blocks: 1, cargoSpaces: 10 }], factories: [] },
			{ ...game.players[1], warehouses: [], factories: [{ system: 'TAU_CETI', goods: 'ISO', size: 3 }] },
		],
	};

	it("lists every corporation's warehouses and factories at the system, to everyone, and none elsewhere", () => {
		const holdings = systemHoldings(built, { ...tauCeti, producibleGoods: ['ISO'] }, 'someone-else');
		assert.deepEqual(holdings.warehouses, [{ system: 'TAU_CETI', blocks: 2, cargoSpaces: 20, corporationName: 'Orion Freight', own: false, held: '\u2014' }]);
		assert.deepEqual(holdings.factories, [{ system: 'TAU_CETI', goods: 'ISO', size: 3, corporationName: 'Vega Lines', own: false }]);
	});

	it('shows what your own warehouse holds, and nobody else\'s (creator, 2026-11-18; R29)', () => {
		const mine = { ...built, you: { warehouses: [{ system: 'TAU_CETI', cargo: { ISO: 3, FOOD: 2 } }, { system: 'MU_HERCULIS', cargo: {} }] } };

		assert.equal(systemHoldings(mine, tauCeti, 'ada').warehouses[0].held, '3 ISO, 2 FOOD');
		assert.equal(systemHoldings(mine, { ...tauCeti, id: 'MU_HERCULIS' }, 'ada').warehouses[0].held, 'Empty');
		assert.equal(systemHoldings(mine, tauCeti, 'someone-else').warehouses[0].held, '\u2014');
	});

	it('names the goods the system can produce, and none where no factory can stand', () => {
		assert.deepEqual(systemHoldings(built, { ...tauCeti, producibleGoods: ['ISO'] }).producible, ['ISO']);
		assert.deepEqual(systemHoldings(built, { ...tauCeti, producibleGoods: [] }).producible, []);
	});

	it('reads a response without warehouses, factories or producible goods as having none', () => {
		const holdings = systemHoldings(game, tauCeti);
		assert.deepEqual(holdings, { producible: [], warehouses: [], factories: [] });
	});
});

describe('staleText (S02-12)', () => {
	it('says how old the state on screen is', () => {
		assert.equal(staleText(12.4), "Couldn't refresh the game. Showing the state from 12 seconds ago.");
		assert.equal(staleText(1), "Couldn't refresh the game. Showing the state from 1 second ago.");
	});

	it('keeps the announced sentence apart from the age, which changes on every failed poll', () => {
		assert.equal(STALE_MESSAGE, "Couldn't refresh the game.");
		assert.equal(staleAgeText(0.4), 'Showing the state from 0 seconds ago.');
	});
});

describe('jumpArrows (R68)', () => {
	const systems = [
		{ id: 'GAMMA_LEPORIS', name: 'Gamma Leporis', jumpChances: { BETA_HYDRI: 7 } },
		{ id: 'BETA_HYDRI', name: 'Beta Hydri', jumpChances: { GAMMA_LEPORIS: 5 } },
	];

	it('puts one arrowhead per direction outside the circle, pointing at the other system, with its chance', () => {
		const [there, back] = jumpArrows(systems);
		// Gamma Leporis (245, 101) → Beta Hydri (515, 101): straight right, tip 64 + 34 out, centroid 64 + 14 out.
		assert.deepEqual(there, { from: 'GAMMA_LEPORIS', to: 'BETA_HYDRI', chance: 7, points: '313,115 313,87 343,101', x: 323, y: 101, level: 'high' });
		assert.deepEqual(back, { from: 'BETA_HYDRI', to: 'GAMMA_LEPORIS', chance: 5, points: '447,87 447,115 417,101', x: 437, y: 101, level: 'high' });
	});

	it('draws nothing for a system without chances', () => {
		assert.deepEqual(jumpArrows([{ id: 'TAU_CETI' }]), []);
	});

	it('marks the three highest different chances high, the three lowest low and the rest mid', () => {
		// The creator's table (R68) has the chances 2 to 9.
		const chances = [2, 2, 3, 4, 4, 5, 6, 6, 7, 8, 9, 9];

		assert.deepEqual([2, 3, 4, 5, 6, 7, 8, 9].map(chance => jumpChanceLevel(chances, chance)), ['low', 'low', 'low', 'mid', 'mid', 'high', 'high', 'high']);
	});

	it('lets high win when there are too few different chances for both ends', () => {
		assert.deepEqual([4, 5, 6, 7].map(chance => jumpChanceLevel([4, 5, 6, 7], chance)), ['low', 'high', 'high', 'high']);
	});

	it('names the chances from a system in board order for its accessible name', () => {
		assert.equal(jumpChancesText(systems, systems[0]), 'Base jump chances from here: Beta Hydri 7.');
		assert.equal(jumpChancesText(systems, { id: 'TAU_CETI' }), '');
	});
});

describe('nodeLabels and mapViewBox (R68)', () => {
	it("stacks the top row's ships above its name, clear of the arrowheads below it", () => {
		assert.deepEqual(nodeLabels('GAMMA_LEPORIS', 2), { name: 25, ships: [-27, -7] });
	});

	it("puts the bottom row's name and ships under its circle, clear of the arrowheads above it", () => {
		assert.deepEqual(nodeLabels('TAU_CETI', 2), { name: 665, ships: [689, 709] });
	});

	it("keeps the middle row's name above and ships below, further out", () => {
		assert.deepEqual(nodeLabels('EPSILON_ERIDANI', 1), { name: 243, ships: [439] });
	});

	it('grows the viewBox to hold every label', () => {
		assert.equal(mapViewBox([nodeLabels('GAMMA_LEPORIS', 2), nodeLabels('TAU_CETI', 1)]), '-20 -51 800 750');
		assert.equal(mapViewBox([{ name: 300, ships: [320] }]), '-20 0 800 670');
	});
});
