import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	MAP_VIEW_BOX,
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
	systemAccessibleName,
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
			email: 'ada@example.com',
			corporationName: 'Orion Freight',
			ships: [
				{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' },
				{ name: 'Lantern', system: 'BETA_HYDRI', systemName: 'Beta Hydri', location: 'SPACE' },
			],
			marketPositions: [{ system: 'TAU_CETI', goods: 'POLY', tier: 'WHOLESALER' }],
		},
		{
			email: 'grace@example.com',
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
		assert.equal(NODE_POSITIONS.SIGMA_DRACONIS[1], NODE_POSITIONS.MU_HERCULIS[1]);
		assert.equal(NODE_POSITIONS.TAU_CETI[1], NODE_POSITIONS.EPSILON_ERIDANI[1]);
		assert.ok(NODE_POSITIONS.SIGMA_DRACONIS[0] < NODE_POSITIONS.GAMMA_LEPORIS[0]);
		assert.ok(NODE_POSITIONS.MU_HERCULIS[0] > NODE_POSITIONS.BETA_HYDRI[0]);
		assert.match(MAP_VIEW_BOX, /^-?\d+ \d+ \d+ \d+$/);
	});

	it('connects every pair of systems once', () => {
		const edges = mapEdges(Object.keys(NODE_POSITIONS));
		assert.equal(edges.length, 15);
		assert.equal(new Set(edges.map(edge => edge.join('-'))).size, 15);
	});

	it('lists a good with its triangle and price, and a steady price on its own', () => {
		const good = mapGood({ goods: 'POLY', price: 6, indicator: -10 });
		assert.equal(good.goods, 'POLY');
		assert.equal(good.symbol, '▲');
		assert.equal(good.price, '\u00a06');
		assert.equal(good.level.key, 'high-demand');
		assert.equal(mapGood({ goods: 'ISO', price: 10, indicator: -7 }).symbol, '');
		assert.equal(mapGood({ goods: 'ISO', price: 10, indicator: -7 }).price, '10');
	});

	it('puts every good, its price and its market label in words in the accessible name', () => {
		assert.equal(
			systemAccessibleName(game, tauCeti, 'ada@example.com'),
			'Tau Ceti: ISO 4 HT, moderate demand, price may rise; POLY 6 HT, high demand, price likely to rise. '
				+ '1 of your ships, 1 other ship. Open system details.',
		);
	});
});

describe('ships under a system (S02-4)', () => {
	it('shows your ships by name first and other players by corporation initials', () => {
		assert.equal(shipsUnderText(game, 'TAU_CETI', 'ada@example.com'), '■ Wayfarer  □ VL');
		assert.equal(shipsUnderText(game, 'TAU_CETI', 'grace@example.com'), '■ Meridian  □ OF');
	});

	it('says "no ships" for an empty system', () => {
		assert.equal(shipsUnderText(game, 'MU_HERCULIS', 'ada@example.com'), 'no ships');
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
		assert.deepEqual(fleet(game, 'ada@example.com').map(fleetLine), [
			'Wayfarer — Tau Ceti · Port — POLY 4, ISO 3, CHIPS 12 · cargo 7 / 10 used',
			'Lantern — Beta Hydri · Space — Empty · cargo 0 / 10 used',
		]);
	});

	it('links each entry to its system', () => {
		assert.deepEqual(fleet(game, 'ada@example.com').map(entry => entry.systemId), ['TAU_CETI', 'BETA_HYDRI']);
	});

	it('leaves out goods with no units', () => {
		assert.equal(cargoText({ FOOD: 0, ISO: 2 }), 'ISO 2');
		assert.equal(cargoText({}), 'Empty');
	});
});

describe('System details locations (S02-7)', () => {
	it('gives each location its statistics and its ships', () => {
		const [port, space, planet] = locations(game, tauCeti, 'ada@example.com');
		assert.deepEqual([port.name, port.stats], ['Port', 'Class 4 · Legal system 2']);
		assert.deepEqual([space.name, space.stats], ['Space', 'Police 3']);
		assert.deepEqual([planet.name, planet.stats], ['Planet', 'Security 4']);
		assert.deepEqual(port.ships.map(ship => ship.text), ['■ Wayfarer — you · POLY 4, ISO 3, CHIPS 12 · cargo 7 / 10 used']);
		assert.deepEqual(space.ships, []);
	});

	it("names another player's ship and its owner, never its cargo", () => {
		const planet = locations(game, tauCeti, 'ada@example.com')[2];
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
