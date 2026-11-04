import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { EMPTY_LOG_TEXT, foldKey, logTurns } from '../../main/resources/static/js/log.js';

describe('logTurns', () => {
	it('groups the events by turn, newest turn first, each turn in the order it happened (S06-1)', () => {
		const log = [
			{ turn: 1, text: 'Game started: 2 players, 20 turns' },
			{ turn: 1, text: 'Starting positions: …' },
			{ turn: 1, text: 'Initiative order (bid + d2): Vega Lines → Orion Freight' },
			{ turn: 2, text: 'Initiative order (bid + d2): Orion Freight → Vega Lines' },
		];

		assert.deepEqual(logTurns(log), [
			{ turn: 2, title: 'Turn 2', events: ['Initiative order (bid + d2): Orion Freight → Vega Lines'] },
			{
				turn: 1,
				title: 'Turn 1',
				events: ['Game started: 2 players, 20 turns', 'Starting positions: …', 'Initiative order (bid + d2): Vega Lines → Orion Freight'],
			},
		]);
	});

	it('orders turns as numbers, not text', () => {
		const log = [2, 10, 9].map(turn => ({ turn, text: `t${turn}` }));

		assert.deepEqual(logTurns(log).map(group => group.turn), [10, 9, 2]);
	});

	it('has no groups when nothing has happened yet (S06-4)', () => {
		assert.deepEqual(logTurns([]), []);
		assert.deepEqual(logTurns(undefined), []);
		assert.equal(EMPTY_LOG_TEXT, 'No public events yet.');
	});
});

describe('foldKey', () => {
	it('keys a folded turn by its game, so it never folds the same turn in another game', () => {
		assert.equal(foldKey('g-1', 3), 'g-1/3');
		assert.notEqual(foldKey('g-1', 3), foldKey('g-2', 3));
	});
});
