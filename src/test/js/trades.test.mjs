import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { SHORT_LIST, shownTrades, yourTrades } from '../../main/resources/static/js/trades.js';

describe('yourTrades (creator, 2026-11-18)', () => {
	const trade = (playerId, type, units, price) => ({ playerId, system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'FOOD', type, units, price });
	const log = [
		{ turn: 1, text: 'Game started', trade: null },
		{ turn: 2, text: 'Orion Freight could buy …', trade: trade('p1', 'BUY', 4, 12) },
		{ turn: 2, text: 'Vega Lines could sell …', trade: trade('p2', 'SELL', 3, 9) },
		{ turn: 3, text: 'Orion Freight could sell …', trade: trade('p1', 'SELL', 2, 15) },
		{ turn: 3, text: 'An entry logged before trades were kept' },
	];

	it('lists only the viewer\'s trades, newest first, coloured by Bought or Sold', () => {
		assert.deepEqual(yourTrades(log, 'p1'), [
			{ turn: 'T3', verb: 'Sold', tone: 'sell', text: '2 FOOD at Tau Ceti, 15 HT each (30 HT)', system: 'TAU_CETI' },
			{ turn: 'T2', verb: 'Bought', tone: 'buy', text: '4 FOOD at Tau Ceti, 12 HT each (48 HT)', system: 'TAU_CETI' },
		]);
	});

	it('has nothing for a player who hasn\'t traded, or a log without trades', () => {
		assert.deepEqual(yourTrades(log, 'p3'), []);
		assert.deepEqual(yourTrades(undefined, 'p1'), []);
	});
});

describe('shownTrades', () => {
	const many = Array.from({ length: SHORT_LIST + 3 }, (_, index) => index);

	it('shows the newest few, saying how many more there are, and all once expanded', () => {
		assert.deepEqual(shownTrades(many, false), { shown: many.slice(0, SHORT_LIST), more: 3 });
		assert.deepEqual(shownTrades(many, true), { shown: many, more: 0 });
		assert.deepEqual(shownTrades([1, 2], false), { shown: [1, 2], more: 0 });
	});
});
