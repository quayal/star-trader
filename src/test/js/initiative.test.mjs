import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	BID_NEGATIVE,
	BID_WHOLE,
	bidButtonLabel,
	bidLine,
	bidStatus,
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

	it('is nothing while Bidding is open: the bid box shows it then (playtest, 2026-11-21)', () => {
		assert.equal(bidLine(game({ bid: 40 })), null);
		assert.equal(bidLine(game({ bid: 0 })), null);
	});

	it('says it was paid once Bidding has closed', () => {
		assert.equal(bidLine(game({ bid: 40, phase: 'JUMPS' })), 'Initiative bid: 40 HT (paid)');
	});
});

describe('bidStatus (playtest, 2026-11-21)', () => {
	it('shows the placed bid, and that it can still change', () => {
		const status = bidStatus(game({ bid: 40 }));
		assert.equal(status.value, '40 HT');
		assert.equal(status.note, 'You can change it until Bidding closes.');
		assert.equal(bidStatus(game({ bid: 0 })).value, '0 HT');
	});

	it('says no bid is placed yet, and that none is needed', () => {
		const status = bidStatus(game());
		assert.equal(status.value, 'None yet');
		assert.equal(status.note, 'Optional.');
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
		assert.equal(initiativeText(game({ phase: 'JUMPS', initiative: order })), 'Initiative: Vega Lines → Orion Freight');
		assert.equal(initiativeText(game({ phase: 'TRADING', initiative: order })), 'Initiative: Vega Lines → Orion Freight');
	});

	it('is nothing during Bidding or without an order', () => {
		assert.equal(initiativeText(game()), null);
		assert.equal(initiativeText(game({ phase: 'JUMPS' })), null);
	});
});

describe('initiativeRolls (creator, 2026-11-18)', () => {
	const players = [{ id: 'p1', corporationName: 'Orion Freight' }, { id: 'p2', corporationName: 'Vega Lines' }];
	const rolls = [
		{ playerId: 'p2', bid: 40, roll: 11, total: 51, tieRolls: [] },
		{ playerId: 'p1', bid: 0, roll: 7, total: 7, tieRolls: [9, 4] },
	];
	const withWolf = [{ playerId: 'p2', bid: 5, roll: 3, bonus: 4, bonusFrom: 'Wolf', total: 12, tieRolls: [] }];

	it('shows what each player bid and rolled, in initiative order, with any tie rolls', () => {
		const game = { status: 'RUNNING', players, turn: { phase: 'JUMPS', initiative: ['p2', 'p1'], initiativeRolls: rolls } };

		assert.deepEqual(initiativeRolls(game), [
			{ corporation: 'Vega Lines', sum: '40 + 11 = 51', tie: '' },
			{ corporation: 'Orion Freight', sum: '0 + 7 = 7', tie: 'tie rolled again: 9, 4' },
		]);
	});

	it("names Wolf's bonus in the sum, which reveals him (R105, playtest 2026-11-20)", () => {
		const game = { status: 'RUNNING', players, turn: { phase: 'JUMPS', initiative: ['p2'], initiativeRolls: withWolf } };

		assert.deepEqual(initiativeRolls(game), [{ corporation: 'Vega Lines', sum: '5 + 3 + Wolf 4 = 12', tie: '' }]);
	});

	it('shows nothing while Bidding is open, or for a turn saved before rolls were kept', () => {
		assert.deepEqual(initiativeRolls({ status: 'RUNNING', players, turn: { phase: 'BIDDING', initiativeRolls: [] } }), []);
		assert.deepEqual(initiativeRolls({ status: 'RUNNING', players, turn: { phase: 'TRADING', initiative: ['p1', 'p2'] } }), []);
	});
});
