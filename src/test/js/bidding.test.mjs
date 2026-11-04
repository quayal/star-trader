import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	ALREADY_CLOSED,
	CLOSED_BY_YOU,
	MARKED_DONE,
	doneAcceptedText,
	doneRefusedText,
	isDone,
	phaseText,
	waitingFor,
} from '../../main/resources/static/js/bidding.js';

const ME = 'me@example.com';

function game({ phase = 'BIDDING', doneBidding = [], status = 'RUNNING' } = {}) {
	return {
		status,
		turns: 20,
		turn: { number: 4, phase, doneBidding },
		players: [
			{ email: 'vega@example.com', corporationName: 'Vega Lines' },
			{ email: ME, corporationName: 'Orion Freight' },
			{ email: 'kessler@example.com', corporationName: 'Kessler Cargo' },
		],
	};
}

describe('waitingFor', () => {
	it('names you first, then the others in join order, while nobody is done', () => {
		assert.deepEqual(waitingFor(game(), ME), ['you', 'Vega Lines', 'Kessler Cargo']);
	});

	it('leaves out players who signalled done', () => {
		assert.deepEqual(waitingFor(game({ doneBidding: ['vega@example.com'] }), ME), ['you', 'Kessler Cargo']);
	});

	it('leaves out you once you signalled done', () => {
		assert.deepEqual(waitingFor(game({ doneBidding: [ME] }), ME), ['Vega Lines', 'Kessler Cargo']);
	});

	it('waits for nobody outside Bidding', () => {
		assert.deepEqual(waitingFor(game({ phase: 'SHIP_JUMPS' }), ME), []);
		assert.deepEqual(waitingFor(game({ status: 'FINISHED', phase: 'TRADING' }), ME), []);
	});
});

describe('isDone', () => {
	it('is true only for a player in the done list during Bidding', () => {
		assert.equal(isDone(game(), ME), false);
		assert.equal(isDone(game({ doneBidding: [ME] }), ME), true);
		assert.equal(isDone(game({ doneBidding: ['vega@example.com'] }), ME), false);
	});

	it('is false outside Bidding', () => {
		assert.equal(isDone(game({ phase: 'SHIP_JUMPS' }), ME), false);
	});
});

describe('phaseText', () => {
	it('names who the game is waiting for during Bidding', () => {
		assert.equal(phaseText(game({ doneBidding: [ME] }), ME), 'Turn 4 of 20 · Bidding — waiting for Vega Lines, Kessler Cargo');
		assert.equal(phaseText(game(), ME), 'Turn 4 of 20 · Bidding — waiting for you, Vega Lines, Kessler Cargo');
	});

	it('is the plain turn text outside Bidding', () => {
		assert.equal(phaseText(game({ phase: 'SHIP_JUMPS' }), ME), 'Turn 4 of 20 · Ship-jumps');
	});
});

describe('doneAcceptedText', () => {
	it('says you can still add offers while others bid', () => {
		assert.equal(doneAcceptedText(game({ doneBidding: [ME] })), MARKED_DONE);
	});

	it('says Bidding closed when your signal was the last', () => {
		assert.equal(doneAcceptedText(game({ phase: 'SHIP_JUMPS' })), CLOSED_BY_YOU);
	});
});

describe('doneRefusedText', () => {
	it('words a close that raced the click as already closed', () => {
		assert.equal(doneRefusedText(409, 'Bidding has closed', 'x'), ALREADY_CLOSED);
	});

	it('shows the server message otherwise, or the fallback', () => {
		assert.equal(doneRefusedText(403, 'Not a player', 'x'), 'Not a player');
		assert.equal(doneRefusedText(500, '', 'Try again.'), 'Try again.');
	});
});
