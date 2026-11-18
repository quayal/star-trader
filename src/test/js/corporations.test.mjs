import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { NO_TEXT, connectionPointsText, connectionsText, corporationCards } from '../../main/resources/static/js/corporations.js';

const board = [
	{ id: 'TAU_CETI', name: 'Tau Ceti' },
	{ id: 'MU_HERCULIS', name: 'Mu Herculis' },
	{ id: 'SIGMA_DRACONIS', name: 'Sigma Draconis' },
];

const vega = {
	id: 'vega',
	name: 'Ada',
	corporationName: 'Vega Lines',
	ships: [
		{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' },
		{ name: 'Lantern', system: 'MU_HERCULIS', systemName: 'Mu Herculis', location: 'SHIPYARD' },
	],
	marketPositions: [
		{ system: 'TAU_CETI', goods: 'ISO', tier: 'RETAILER' },
		{ system: 'MU_HERCULIS', goods: 'ISO', tier: 'DICTATOR' },
		{ system: 'MU_HERCULIS', goods: 'CHIPS', tier: 'WHOLESALER' },
		{ system: 'MU_HERCULIS', goods: 'FOOD', tier: 'DICTATOR' },
	],
	warehouses: [{ system: 'TAU_CETI', blocks: 3, cargoSpaces: 30 }],
	factories: [{ system: 'SIGMA_DRACONIS', goods: 'ISO', size: 4 }],
	connections: { political: 3, business: 4, criminal: 0 },
	connectionPoints: 0,
};
const orion = { id: 'me', name: 'Grace', corporationName: 'Orion Freight', ships: [], marketPositions: [], warehouses: [], factories: [] };
const altair = {
	id: 'altair',
	name: 'Linus',
	corporationName: 'Altair Trade Co.',
	ships: [{ name: 'Meridian', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SPACE' }],
	warehouses: [{ system: 'MU_HERCULIS', blocks: 1, cargoSpaces: 10 }],
	factories: [{ system: 'TAU_CETI', goods: 'ISO', size: 1 }],
};

const game = { board, players: [vega, orion, altair], you: { money: 300, ships: [{ name: 'secret hull data' }] } };

describe('connections on the Corporations tab (E12-S01-10)', () => {
	it('shows every connection, public to everyone (R81)', () => {
		assert.equal(corporationCards(game, 'me')[1].connections, 'Political 3 \u00b7 Business 4 \u00b7 Criminal 0');
		assert.equal(connectionsText(undefined), 'Political 0 \u00b7 Business 0 \u00b7 Criminal 0');
	});

	it('says how many starting points are still to place, and nothing once placed (R82)', () => {
		assert.equal(corporationCards(game, 'me')[1].connectionPoints, null);
		assert.equal(connectionPointsText(5), '5 points to place');
		assert.equal(connectionPointsText(1), '1 point to place');
		assert.equal(connectionPointsText(0), null);
		assert.equal(connectionPointsText(undefined), null);
	});
});

describe('corporationCards (E11-S01)', () => {
	it('lists every corporation in join order, the viewer first and marked as you', () => {
		const cards = corporationCards(game, 'me');

		assert.deepEqual(cards.map(card => [card.corporationName, card.playerName, card.you]), [
			['Orion Freight', 'Grace', true],
			['Vega Lines', 'Ada', false],
			['Altair Trade Co.', 'Linus', false],
		]);
	});

	it("shows each ship's name, system and location, sorted by name (R57)", () => {
		const vegaCard = corporationCards(game, 'me')[1];

		assert.deepEqual(vegaCard.ships, ['Lantern — Mu Herculis, Shipyard', 'Wayfarer — Tau Ceti, Port']);
		assert.deepEqual(corporationCards(game, 'me')[2].ships, ['Meridian — Tau Ceti, Space']);
	});

	it('shows market positions, highest tier first, then by system and good (R28)', () => {
		assert.deepEqual(corporationCards(game, 'me')[1].marketPositions, [
			{ tier: 'Dictator', goods: 'FOOD', systemName: 'Mu Herculis' },
			{ tier: 'Dictator', goods: 'ISO', systemName: 'Mu Herculis' },
			{ tier: 'Wholesaler', goods: 'CHIPS', systemName: 'Mu Herculis' },
			{ tier: 'Retailer', goods: 'ISO', systemName: 'Tau Ceti' },
		]);
	});

	it('shows warehouses with their capacity and factories with their size, never their contents (R50)', () => {
		const vegaCard = corporationCards(game, 'me')[1];

		assert.deepEqual(vegaCard.warehouses, ['Tau Ceti — 3 blocks, 30 cargo spaces']);
		assert.deepEqual(vegaCard.factories, [{ goods: 'ISO', systemName: 'Sigma Draconis', text: ' at Sigma Draconis — 4 u per turn' }]);
		assert.deepEqual(corporationCards(game, 'me')[2].warehouses, ['Mu Herculis — 1 block, 10 cargo spaces']);
	});

	it('gives every empty section its own line, and reads a missing list as empty', () => {
		const mine = corporationCards(game, 'me')[0];

		assert.deepEqual([mine.ships, mine.marketPositions, mine.warehouses, mine.factories], [[], [], [], []]);
		assert.deepEqual(NO_TEXT, {
			ships: 'No ships',
			marketPositions: 'No market positions',
			warehouses: 'No warehouses',
			factories: 'No factories',
		});
		assert.deepEqual(corporationCards(game, 'me')[2].marketPositions, []);
	});

	it('never carries money or private ship details (R29, R57)', () => {
		const text = JSON.stringify(corporationCards(game, 'me'));

		assert.ok(!text.includes('300'));
		assert.ok(!text.includes('secret hull data'));
		assert.ok(!text.includes('money'));
	});

	it('keeps join order for a viewer who is not a player', () => {
		assert.deepEqual(corporationCards(game, 'nobody').map(card => card.corporationName), ['Vega Lines', 'Orion Freight', 'Altair Trade Co.']);
	});
});
