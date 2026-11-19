import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	factoryCap,
	isPlacing,
	maxFactoryUnits,
	parseLevel,
	placementTotalText,
	pointsToPlace,
	RAISED_TEXT,
	raiseButtons,
	validatePlacement,
	canRaise,
} from '../../main/resources/static/js/connections.js';
import { validateUnits } from '../../main/resources/static/js/investment.js';

const bidding = (points) => ({
	status: 'RUNNING',
	turn: { number: 1, phase: 'BIDDING', doneBidding: [] },
	players: [
		{ id: 'p1', connections: { political: 0, business: 0, criminal: 0 }, connectionPoints: points },
		{ id: 'p2', connections: { political: 0, business: 0, criminal: 0 }, connectionPoints: 4 },
	],
	you: { id: 'p1', money: 300 },
});

const investing = (invest, money = 300) => ({
	status: 'RUNNING',
	turn: { number: 2, phase: 'INVESTMENT', doneInvesting: [] },
	players: [{ id: 'p1', connections: { political: 3, business: 4, criminal: 0 }, connectionPoints: 0 }],
	you: { id: 'p1', money, invest },
});

const invest = {
	factoryUnits: 5,
	factoryCap: 7,
	raisedConnection: false,
	connections: [
		{ kind: 'POLITICAL', name: 'political', level: 3, cost: 40 },
		{ kind: 'BUSINESS', name: 'business', level: 4, cost: 50 },
		{ kind: 'CRIMINAL', name: 'criminal', level: 10, cost: null },
	],
};

describe('placing the starting connection points (E12-S01)', () => {
	it('shows the form in Bidding only while the viewer has points to place (R82)', () => {
		assert.equal(pointsToPlace(bidding(5)), 5);
		assert.equal(isPlacing(bidding(5)), true);
		assert.equal(isPlacing(bidding(0)), false);
		assert.equal(isPlacing({ ...bidding(5), turn: { number: 1, phase: 'SHIP_JUMPS' } }), false);
	});

	it('reads blank as 0 and refuses anything but a whole number from 0 to 10', () => {
		assert.equal(parseLevel(''), 0);
		assert.equal(parseLevel(' 3 '), 3);
		assert.equal(parseLevel('11'), null);
		assert.equal(parseLevel('2.5'), null);
		assert.equal(parseLevel('-1'), null);
	});

	it('counts the points placed as they are typed', () => {
		assert.equal(placementTotalText({ political: '2', business: '1', criminal: '' }, 5), '3 of 5 points placed');
		assert.equal(placementTotalText({ political: 'x', business: '1', criminal: '' }, 5), '');
	});

	it('sends a placement that adds up, and refuses others with the server\'s words', () => {
		assert.deepEqual(validatePlacement({ political: '2', business: '3', criminal: '0' }, 5), {
			placement: { political: 2, business: 3, criminal: 0 },
		});
		assert.deepEqual(validatePlacement({ political: '2', business: '2', criminal: '0' }, 5), {
			error: 'Your connections must add up to 5 points.',
		});
		assert.deepEqual(validatePlacement({ political: '12', business: '0', criminal: '0' }, 5), {
			error: 'Enter a whole number from 0 to 10 for each connection.',
		});
	});
});

describe('raising a connection (E12-S02)', () => {
	it('allows one raise per Investment', () => {
		assert.equal(canRaise(investing(invest)), true);
		assert.equal(canRaise(investing({ ...invest, raisedConnection: true })), false);
		assert.equal(canRaise(bidding(0)), false);
	});
});

describe('the factory cap (E12-S03)', () => {
	it('shows the units owned out of political + business (R84)', () => {
		assert.deepEqual(factoryCap(investing(invest)), {
			text: 'Factory units: 5 of 7 (political 3 + business 4)',
			room: 2,
			refusal: 'You can only own 7 units of factory (POL: 3, BUSS: 4).',
		});
		assert.equal(factoryCap(bidding(0)), null);
	});

	it('takes the smaller of what the viewer can afford and what the cap allows as the most to add', () => {
		const factory = { unitCost: 70 };
		assert.equal(maxFactoryUnits(investing(invest, 300), factory), 2);
		assert.equal(maxFactoryUnits(investing(invest, 100), factory), 1);
		assert.equal(maxFactoryUnits(investing({ ...invest, factoryUnits: 9 }, 300), factory), 0);
	});

	it('refuses units beyond the cap before the money, as the server does', () => {
		const cap = factoryCap(investing(invest));
		assert.deepEqual(validateUnits('3', { unitCost: 70 }, 30, cap), { error: 'You can only own 7 units of factory (POL: 3, BUSS: 4).' });
		assert.deepEqual(validateUnits('2', { unitCost: 70 }, 300, cap), { units: 2 });
	});
});

describe('raise buttons on the Corporations card (creator, 2026-11-19)', () => {
	it('offers each connection with its next level and cost, disabled with the reason at 10 or short of money', () => {
		assert.deepEqual(raiseButtons(investing(invest, 45)), {
			raised: '',
			buttons: [
				{ kind: 'POLITICAL', label: 'Raise Political to 4 \u2014 40 HT', disabled: false, reason: '' },
				{ kind: 'BUSINESS', label: 'Raise Business to 5 \u2014 50 HT', disabled: true, reason: 'You have 45 HT.' },
				{ kind: 'CRIMINAL', label: 'Raise Criminal', disabled: true, reason: 'Criminal is at 10, the most there is.' },
			],
		});
	});

	it('says a connection was raised this Investment, and shows nothing outside it', () => {
		assert.deepEqual(raiseButtons(investing({ ...invest, raisedConnection: true })), { raised: RAISED_TEXT, buttons: [] });
		assert.equal(raiseButtons(bidding(0)), null);
	});
});
