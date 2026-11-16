import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { EMPTY_LOG_TEXT, foldKey, logAfterQuery, logTurns, mergeLog } from '../../main/resources/static/js/log.js';

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

describe('the poll asks only for the log entries it does not have', () => {
	const A = { turn: 1, text: 'Game started' };
	const B = { turn: 1, text: 'Starting positions' };
	const C = { turn: 1, text: 'Initiative order' };
	const known = { id: 'g-1', entries: [A, B] };

	it('asks for the entries after the ones it has for that game', () => {
		assert.equal(logAfterQuery(known, 'g-1'), '?logAfter=2');
	});

	it('asks for the whole log with nothing known, an empty log, or another game', () => {
		assert.equal(logAfterQuery(null, 'g-1'), '');
		assert.equal(logAfterQuery({ id: 'g-1', entries: [] }, 'g-1'), '');
		assert.equal(logAfterQuery(known, 'g-2'), '');
	});

	it('joins the newer entries to the ones it had', () => {
		assert.deepEqual(mergeLog(known, { id: 'g-1', logOffset: 2, log: [C] }), [A, B, C]);
		assert.deepEqual(mergeLog(known, { id: 'g-1', logOffset: 2, log: [] }), [A, B]);
	});

	it('takes a whole log as it is', () => {
		assert.deepEqual(mergeLog(known, { id: 'g-1', logOffset: 0, log: [A] }), [A]);
		assert.deepEqual(mergeLog(null, { id: 'g-1', log: [A, B] }), [A, B]);
	});

	it('drops what it learnt meanwhile and keeps what the answer follows on from', () => {
		// an action's answer brought C while the poll was in flight; the poll was sent knowing two entries
		assert.deepEqual(mergeLog({ id: 'g-1', entries: [A, B, C] }, { id: 'g-1', logOffset: 2, log: [C] }), [A, B, C]);
	});

	it("can't join an answer that doesn't follow on from what it has", () => {
		assert.equal(mergeLog(known, { id: 'g-1', logOffset: 3, log: [C] }), null);
		assert.equal(mergeLog(known, { id: 'g-2', logOffset: 2, log: [C] }), null);
		assert.equal(mergeLog(null, { id: 'g-1', logOffset: 2, log: [C] }), null);
	});
});
