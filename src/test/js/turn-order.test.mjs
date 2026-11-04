import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	FREE_PLACE,
	freePlaces,
	ordinal,
	pickingText,
	picker,
	turnOrderRows,
	youActText,
} from '../../main/resources/static/js/turn-order.js';

const ME = 'me@example.com';

function game({ phase = 'SHIP_JUMPS', status = 'RUNNING', turnOrder = [null, null, null] } = {}) {
	return {
		status,
		turn: { number: 4, phase, initiative: ['kessler@example.com', ME, 'vega@example.com'], turnOrder },
		players: [
			{ email: 'vega@example.com', corporationName: 'Vega Lines' },
			{ email: ME, corporationName: 'Orion Freight' },
			{ email: 'kessler@example.com', corporationName: 'Kessler Cargo' },
		],
	};
}

describe('ordinal', () => {
	it('names places up to the sixth', () => {
		assert.deepEqual([1, 2, 3, 4, 5, 6].map(ordinal), ['1st', '2nd', '3rd', '4th', '5th', '6th']);
	});
});

describe('picker', () => {
	it('is the first player in initiative order without a place (R26, R27)', () => {
		assert.equal(picker(game()), 'kessler@example.com');
		assert.equal(picker(game({ turnOrder: [null, null, 'kessler@example.com'] })), ME);
	});

	it('is nobody once the order is set, during Bidding, or when the game is over', () => {
		assert.equal(picker(game({ turnOrder: ['vega@example.com', ME, 'kessler@example.com'] })), null);
		assert.equal(picker(game({ phase: 'BIDDING', turnOrder: [] })), null);
		assert.equal(picker(game({ status: 'FINISHED' })), null);
	});
});

describe('pickingText', () => {
	it('names who is choosing, or asks you to (S01-2)', () => {
		assert.equal(pickingText(game(), ME), 'Kessler Cargo is choosing their place in the turn order');
		assert.equal(pickingText(game({ turnOrder: [null, null, 'kessler@example.com'] }), ME), 'choose your place in the turn order');
		assert.equal(pickingText(game({ turnOrder: ['vega@example.com', ME, 'kessler@example.com'] }), ME), '');
	});
});

describe('turnOrderRows', () => {
	it('lists every place with who holds it, a dash for a free one (S01-3)', () => {
		assert.deepEqual(turnOrderRows(game({ turnOrder: [null, null, 'kessler@example.com'] })), [
			{ place: '1st', holder: FREE_PLACE },
			{ place: '2nd', holder: FREE_PLACE },
			{ place: '3rd', holder: 'Kessler Cargo' },
		]);
	});

	it('is empty during Bidding', () => {
		assert.deepEqual(turnOrderRows(game({ phase: 'BIDDING', turnOrder: [] })), []);
	});
});

describe('freePlaces', () => {
	it('offers the free places only on your pick (S01-4)', () => {
		const yourPick = game({ turnOrder: [null, null, 'kessler@example.com'] });

		assert.deepEqual(freePlaces(yourPick, ME), [1, 2]);
		assert.deepEqual(freePlaces(game(), ME), []);
	});
});

describe('youActText', () => {
	it('tells you your place once you have one (S01-4)', () => {
		assert.equal(youActText(game({ turnOrder: ['vega@example.com', ME, 'kessler@example.com'] }), ME), 'You act 2nd this turn.');
		assert.equal(youActText(game(), ME), '');
	});
});
