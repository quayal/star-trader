import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	YOUR_QUEUE_NOTE,
	decisionText,
	goodLabel,
	lastResolvedText,
	moveGood,
	movedText,
	orderedBy,
	queueBody,
	queueNote,
	resolvedText,
	resolver,
	sameOrder,
	tradingJustStarted,
	waitingText,
} from '../../main/resources/static/js/trading.js';

const ME = 'me@example.com';
const VEGA = 'vega@example.com';

const ISO = { system: 'MU_HERCULIS', systemName: 'Mu Herculis', goods: 'ISO' };
const POLY = { system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'POLY' };
const CHIPS = { system: 'BETA_HYDRI', systemName: 'Beta Hydri', goods: 'CHIPS' };

function game({ id = 'g1', phase = 'TRADING', status = 'RUNNING', number = 1, initiative = [VEGA, ME], queue = [ISO, POLY], done = [] } = {}) {
	return {
		id,
		status,
		turn: { number, phase, initiative, tradingQueue: queue, resolvedGoods: done },
		players: [
			{ email: VEGA, corporationName: 'Vega Lines' },
			{ email: ME, corporationName: 'Orion Freight' },
		],
	};
}

describe('who orders and resolves (R25)', () => {
	it('is the first in initiative, during Trading only', () => {
		assert.equal(orderedBy(game()), VEGA);
		assert.equal(resolver(game()), VEGA);
		assert.equal(orderedBy(game({ phase: 'SHIP_JUMPS' })), null);
		assert.equal(orderedBy(game({ status: 'FINISHED' })), null);
	});

	it('leaves nobody to resolve once the queue is empty', () => {
		assert.equal(resolver(game({ queue: [], done: [ISO, POLY] })), null);
		assert.equal(orderedBy(game({ queue: [], done: [ISO, POLY] })), VEGA);
	});
});

describe('the Trading texts', () => {
	it('counts the resolved goods for the top bar (S01-6)', () => {
		assert.equal(resolvedText(game({ queue: [POLY], done: [ISO] })), '1 of 2 resolved');
		assert.equal(resolvedText(game({ phase: 'SHIP_JUMPS' })), '');
	});

	it('names whom the game waits for (S01-8, D09)', () => {
		assert.equal(waitingText(game(), ME), 'Waiting for Vega Lines to resolve the next good.');
		assert.equal(waitingText(game(), VEGA), 'You are first in initiative: resolve the next good.');
		assert.equal(waitingText(game({ queue: [], done: [ISO, POLY] }), ME), 'All 2 goods resolved.');
		assert.equal(waitingText(game({ phase: 'BIDDING' }), ME), '');
	});

	it('tells everyone else who decides the order (S01-3)', () => {
		assert.equal(queueNote(game(), ME), 'Order decided by Vega Lines (first in initiative); it can change until each one resolves.');
		assert.equal(queueNote(game(), VEGA), YOUR_QUEUE_NOTE);
	});

	it('labels a good and the last one resolved', () => {
		assert.equal(goodLabel(ISO), 'Mu Herculis · ISO');
		assert.equal(lastResolvedText(game({ queue: [CHIPS], done: [ISO, POLY] })), 'Last resolved: Tau Ceti · POLY');
		assert.equal(lastResolvedText(game()), '');
	});
});

describe('reordering (S01-3)', () => {
	it('moves a good up or down, and nowhere past the ends', () => {
		assert.deepEqual(moveGood([ISO, POLY, CHIPS], 2, 0), [CHIPS, ISO, POLY]);
		assert.deepEqual(moveGood([ISO, POLY, CHIPS], 0, 1), [POLY, ISO, CHIPS]);
		assert.deepEqual(moveGood([ISO, POLY], 0, -1), [ISO, POLY]);
		assert.deepEqual(moveGood([ISO, POLY], 1, 2), [ISO, POLY]);
	});

	it('announces where the good went', () => {
		assert.equal(movedText([POLY, ISO, CHIPS], 1), 'Mu Herculis · ISO moved to position 2 of 3.');
	});

	it('sends only the system and good of each', () => {
		assert.deepEqual(queueBody([ISO, POLY]), { queue: [{ system: 'MU_HERCULIS', goods: 'ISO' }, { system: 'TAU_CETI', goods: 'POLY' }] });
	});

	it('compares two orders', () => {
		assert.equal(sameOrder([ISO, POLY], [{ ...ISO }, { ...POLY }]), true);
		assert.equal(sameOrder([ISO, POLY], [POLY, ISO]), false);
		assert.equal(sameOrder([ISO], [ISO, POLY]), false);
	});
});

describe('the decision alert (S01-9)', () => {
	it('asks the first in initiative to resolve the next good, and nobody else', () => {
		assert.equal(decisionText(game(), VEGA), 'Your decision is needed: resolve the next good.');
		assert.equal(decisionText(game(), ME), '');
		assert.equal(decisionText(game({ queue: [] }), VEGA), '');
	});
});

describe('opening Trading when it starts (S01-1)', () => {
	it('only on the step into Trading, for the same game', () => {
		assert.equal(tradingJustStarted(game({ phase: 'SHIP_JUMPS' }), game()), true);
		assert.equal(tradingJustStarted(game({ number: 1 }), game({ number: 2 })), true);
		assert.equal(tradingJustStarted(game(), game()), false);
		assert.equal(tradingJustStarted(null, game()), false);
		assert.equal(tradingJustStarted(game({ id: 'other', phase: 'SHIP_JUMPS' }), game()), false);
		assert.equal(tradingJustStarted(game({ phase: 'SHIP_JUMPS' }), game({ phase: 'SHIP_JUMPS' })), false);
	});
});
