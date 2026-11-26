import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	cellMatches,
	changeText,
	holdings,
	marketColumns,
	marketDetail,
	marketMatrix,
	offersCheck,
	saturationHint,
} from '../../main/resources/static/js/market.js';

const ME = 'me';

function market(goods, price, indicator, extra = {}) {
	return { goods, price, indicator, lastChange: 0, buyCeiling: price + 7 < 20 ? price + 7 : null, sellFloor: price - 6 > 1 ? price - 6 : null, ...extra };
}

const BOARD = [
	{ id: 'TAU_CETI', name: 'Tau Ceti', market: [market('FOOD', 7, 3, { lastChange: 2 }), market('ISO', 4, -9)] },
	{ id: 'BETA_HYDRI', name: 'Beta Hydri', market: [market('FOOD', 14, -2, { lastChange: -1 }), market('ISO', 9, 0)] },
	{ id: 'MU_HERCULIS', name: 'Mu Herculis', market: [market('FOOD', 9, 0), market('ISO', 11, 2)] },
];

function game({ offers = [], cargo = { FOOD: 6, ISO: 2 }, cargoUsed = 8, warehouses = [], phase = 'BIDDING', shipLocation = 'PORT', positions = [] } = {}) {
	return {
		status: 'RUNNING',
		board: BOARD,
		turn: { number: 3, phase, jumpedShips: [] },
		log: [
			{ turn: 2, text: 'trade', trade: { playerId: ME, system: 'TAU_CETI', goods: 'FOOD', type: 'BUY', units: 4, price: 5 } },
			{ turn: 3, text: 'News: Food +3 at Beta Hydri' },
		],
		players: [
			{
				id: ME,
				corporationName: 'Orion Freight',
				marketPositions: positions,
				ships: [{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: shipLocation }],
			},
			{ id: 'vega', corporationName: 'Vega Lines', marketPositions: [{ system: 'BETA_HYDRI', goods: 'FOOD', tier: 'WHOLESALER' }], ships: [] },
		],
		you: {
			id: ME,
			money: 40,
			ships: [{
				name: 'Wayfarer',
				cargo,
				cargoUsed,
				cargoSpaces: 8,
				jumpChances: { BETA_HYDRI: { target: 7, percent: 58 }, MU_HERCULIS: { target: 10, percent: 92 } },
			}],
			warehouses,
			offers,
			expeditions: [],
			tipOffs: [],
		},
	};
}

const offer = (system, systemName, goods, type, price) => ({ system, systemName, goods, type, price });

describe('Market: holdings and columns (criteria 5, 11)', () => {
	it('lists each ship and warehouse with what it holds and its free space', () => {
		const chips = holdings(game({ warehouses: [{ system: 'BETA_HYDRI', systemName: 'Beta Hydri', cargo: { POLY: 10 }, cargoUsed: 10, cargoSpaces: 20 }] }), ME);
		assert.deepEqual(chips.map(chip => chip.text), [
			'Wayfarer · Tau Ceti Port · FOOD 6, ISO 2 · free 0/8',
			'Warehouse · Beta Hydri · POLY 10 · free 10/20',
		]);
		assert.equal(chips[0].canJump, true);
	});

	it('says what is where now and the best reach elsewhere', () => {
		const columns = marketColumns(game(), ME);
		assert.deepEqual(columns.map(c => [c.name, c.presence, c.reach]), [
			['Tau Ceti', 'Wayfarer (Port)', 'here'],
			['Beta Hydri', '', 'reach 58% (Wayfarer)'],
			['Mu Herculis', '', 'reach 92% (Wayfarer)'],
		]);
	});

	it('has no reach once the ships can no longer jump this turn', () => {
		assert.equal(marketColumns(game({ phase: 'TRADING' }), ME)[1].reach, '—');
	});
});

describe('Market: the matrix (criteria 3, 4, 6)', () => {
	it('gives each cell its price, move, lean, stock, tier and offer', () => {
		const rows = marketMatrix(game({ offers: [offer('TAU_CETI', 'Tau Ceti', 'FOOD', 'BUY', 7)], positions: [{ system: 'TAU_CETI', goods: 'FOOD', tier: 'RETAILER' }] }), ME);
		const food = rows.find(row => row.goods === 'FOOD');
		const tau = food.cells[0];
		assert.deepEqual([tau.price, tau.change, tau.held, tau.tier, tau.offer, tau.level.key], [7, '▲2', 6, 'R', 'Buy 7', 'high-supply']);
		assert.equal(food.cells[1].change, '▼1');
		assert.equal(rows.find(row => row.goods === 'CHIPS').cells.every(cell => !cell.traded), true);
	});

	it('marks the row\'s cheapest and dearest market and names the spread', () => {
		const food = marketMatrix(game(), ME).find(row => row.goods === 'FOOD');
		assert.deepEqual(food.cells.map(cell => [cell.cheapest, cell.dearest]), [[true, false], [false, true], [false, false]]);
		assert.equal(food.spread, 'FOOD: cheapest Tau Ceti 7, dearest Beta Hydri 14 (spread 7)');
	});

	it('says a Dictator offers in Trading', () => {
		const food = marketMatrix(game({ positions: [{ system: 'TAU_CETI', goods: 'FOOD', tier: 'DICTATOR' }] }), ME).find(row => row.goods === 'FOOD');
		assert.equal(food.cells[0].dictator, true);
	});

	it('dims cells by filter, never removes them', () => {
		const g = game({ offers: [offer('MU_HERCULIS', 'Mu Herculis', 'ISO', 'SELL', 12)] });
		const columns = marketColumns(g, ME);
		const iso = marketMatrix(g, ME).find(row => row.goods === 'ISO').cells;
		assert.deepEqual(iso.map((cell, i) => cellMatches('goods', cell, columns[i])), [true, false, false]);
		assert.deepEqual(iso.map((cell, i) => cellMatches('reach', cell, columns[i])), [true, true, true]);
		assert.deepEqual(iso.map((cell, i) => cellMatches('offers', cell, columns[i])), [false, false, true]);
		assert.equal(iso.length, 3);
	});

	it('writes a move as a triangle and a number', () => {
		assert.deepEqual([changeText(2), changeText(-1), changeText(0)], ['▲2', '▼1', '—']);
	});
});

describe('Market: the inspector (criteria 8–10)', () => {
	it('gives the market, last trades, public tiers, news and the viewer\'s side', () => {
		const detail = marketDetail(game(), ME, 'BETA_HYDRI', 'FOOD');
		assert.equal(detail.indicator, 'S/D −2');
		assert.deepEqual(detail.wholesalers, ['Vega Lines']);
		assert.deepEqual(detail.news, ['News: Food +3 at Beta Hydri']);
		assert.deepEqual(detail.elsewhere, ['FOOD: 6 on Wayfarer at Tau Ceti (reach 58%)']);
		assert.equal(detail.cash, 40);
		assert.equal(detail.free, null);
		assert.deepEqual(marketDetail(game(), ME, 'TAU_CETI', 'FOOD').lastTrades, ['Orion Freight bought 4 at 5']);
	});

	it('says where an offer stops counting, from the server\'s R15 limits', () => {
		assert.equal(saturationHint({ buyCeiling: 11, sellFloor: 4 }), 'Buy above 11 or sell below 4 moves no further');
		assert.equal(saturationHint({ buyCeiling: 11, sellFloor: null }), 'Buy above 11 moves no further');
		assert.equal(saturationHint({ buyCeiling: null, sellFloor: null }), '');
	});
});

describe('Market: the offers check (criterion 12)', () => {
	it('warns when an offer relies on a jump, or nothing can be there', () => {
		const g = game({ offers: [offer('BETA_HYDRI', 'Beta Hydri', 'FOOD', 'SELL', 14)] });
		assert.deepEqual(offersCheck(g, ME).map(w => w.text), ['Relies on Wayfarer reaching Beta Hydri (58%).']);
		const stuck = game({ phase: 'BIDDING', shipLocation: 'SHIPYARD', offers: [offer('BETA_HYDRI', 'Beta Hydri', 'FOOD', 'BUY', 14)] });
		assert.deepEqual(offersCheck(stuck, ME).map(w => w.text), ['Nothing of yours can be there. A won buy is lost (R23).']);
	});

	it('warns about a sell with nothing to sell and a buy with no space', () => {
		const g = game({ offers: [offer('TAU_CETI', 'Tau Ceti', 'ISO', 'BUY', 4), offer('TAU_CETI', 'Tau Ceti', 'FOOD', 'SELL', 9)], cargo: {}, cargoUsed: 8 });
		assert.deepEqual(offersCheck(g, ME).map(w => w.text), [
			'No free space can reach Tau Ceti; overflow is lost (R16).',
			'Nothing to sell: no FOOD at Tau Ceti, and no ship with FOOD can reach it.',
		]);
	});

	it('warns when the offers span more systems than the viewer can cover', () => {
		const g = game({ offers: [offer('BETA_HYDRI', 'Beta Hydri', 'FOOD', 'SELL', 14), offer('MU_HERCULIS', 'Mu Herculis', 'FOOD', 'SELL', 9)] });
		assert.equal(offersCheck(g, ME).at(-1).text, 'Offers in 2 systems, but you can cover at most 1 this turn.');
	});

	it('says nothing about offers that can be carried out', () => {
		assert.deepEqual(offersCheck(game({ offers: [offer('TAU_CETI', 'Tau Ceti', 'FOOD', 'SELL', 9)] }), ME), []);
	});
});
