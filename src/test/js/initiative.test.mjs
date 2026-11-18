import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	BID_NEGATIVE,
	BID_WHOLE,
	bidButtonLabel,
	bidLine,
	bidPlacedText,
	initiativeRolls,
	initiativeText,
	overMoneyText,
	validateBid,
} from '../../main/resources/static/js/initiative.js';

function game({ phase = 'BIDDING', status = 'RUNNING', bid = null, initiative = [] } = {}) {
	return {
		status,
		turn: { number: 1, phase, doneBidding: [], initiative },
		players: [
			{ id: 'me', corporationName: 'Orion Freight' },
			{ id: 'vega', corporationName: 'Vega Lines' },
		],
		you: { money: 300, offers: [], bid },
	};
}

describe('validateBid', () => {
	it('accepts whole numbers from 0 up to the money', () => {
		assert.deepEqual(validateBid('0', 300), { amount: 0 });
		assert.deepEqual(validateBid(' 40 ', 300), { amount: 40 });
		assert.deepEqual(validateBid('300', 300), { amount: 300 });
	});

	it('asks for a whole number when empty, fractional or not a number', () => {
		for (const raw of ['', '  ', '2.5', 'ten', '1e2', null]) {
			assert.deepEqual(validateBid(raw, 300), { error: BID_WHOLE }, String(raw));
		}
	});

	it('says a bid cannot be negative', () => {
		assert.deepEqual(validateBid('-5', 300), { error: BID_NEGATIVE });
	});

	it('names the money when the bid is more than the player has', () => {
		assert.deepEqual(validateBid('301', 300), { error: 'You have 300 HT. Enter 300 or less.' });
		assert.equal(overMoneyText(12), 'You have 12 HT. Enter 12 or less.');
	});
});

describe('bidLine', () => {
	it('is nothing before a bid is placed', () => {
		assert.equal(bidLine(game()), null);
	});

	it('says the bid is paid when Bidding closes, win or lose', () => {
		assert.equal(bidLine(game({ bid: 40 })), 'Initiative bid: 40 HT (paid when Bidding closes, win or lose)');
		assert.equal(bidLine(game({ bid: 0 })), 'Initiative bid: 0 HT (paid when Bidding closes, win or lose)');
	});

	it('says it was paid once Bidding has closed', () => {
		assert.equal(bidLine(game({ bid: 40, phase: 'SHIP_JUMPS' })), 'Initiative bid: 40 HT (paid)');
	});
});

describe('bidButtonLabel', () => {
	it('places a first bid and changes a placed one', () => {
		assert.equal(bidButtonLabel(game()), 'Place bid');
		assert.equal(bidButtonLabel(game({ bid: 0 })), 'Change bid');
	});
});

describe('bidPlacedText', () => {
	it('confirms the amount', () => {
		assert.equal(bidPlacedText(25), 'Initiative bid placed: 25 HT.');
	});
});

describe('initiativeText', () => {
	it('names the order by corporation from Ship-jumps on', () => {
		const order = ['vega', 'me'];
		assert.equal(initiativeText(game({ phase: 'SHIP_JUMPS', initiative: order })), 'Initiative: Vega Lines → Orion Freight');
		assert.equal(initiativeText(game({ phase: 'TRADING', initiative: order })), 'Initiative: Vega Lines → Orion Freight');
	});

	it('is nothing during Bidding or without an order', () => {
		assert.equal(initiativeText(game()), null);
		assert.equal(initiativeText(game({ phase: 'SHIP_JUMPS' })), null);
	});
});

describe('initiativeRolls (creator, 2026-11-18)', () => {
	const players = [{ id: 'p1', corporationName: 'Orion Freight' }, { id: 'p2', corporationName: 'Vega Lines' }];
	const rolls = [
		{ playerId: 'p2', bid: 40, roll: 11, total: 51, tieRolls: [] },
		{ playerId: 'p1', bid: 0, roll: 7, total: 7, tieRolls: [9, 4] },
	];

	it('shows what each player bid and rolled, in initiative order, with any tie rolls', () => {
		const game = { status: 'RUNNING', players, turn: { phase: 'SHIP_JUMPS', initiative: ['p2', 'p1'], initiativeRolls: rolls } };

		assert.deepEqual(initiativeRolls(game), [
			{ corporation: 'Vega Lines', sum: '40 + 11 = 51', tie: '' },
			{ corporation: 'Orion Freight', sum: '0 + 7 = 7', tie: 'tie rolled again: 9, 4' },
		]);
	});

	it('shows nothing while Bidding is open, or for a turn saved before rolls were kept', () => {
		assert.deepEqual(initiativeRolls({ status: 'RUNNING', players, turn: { phase: 'BIDDING', initiativeRolls: [] } }), []);
		assert.deepEqual(initiativeRolls({ status: 'RUNNING', players, turn: { phase: 'TRADING', initiative: ['p1', 'p2'] } }), []);
	});
});
