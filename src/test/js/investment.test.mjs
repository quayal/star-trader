import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	ALREADY_CLOSED_INVESTING,
	INVESTMENT_CLOSED_BY_YOU,
	doneInvestingAcceptedText,
	doneInvestingRefusedText,
	isDoneInvesting,
	isInvesting,
	isInvestmentClosedRefusal,
	waitingForInvesting,
} from '../../main/resources/static/js/investment.js';
import { phaseText } from '../../main/resources/static/js/bidding.js';

const ME = 'me@example.com';

function game({ phase = 'INVESTMENT', doneInvesting = [], status = 'RUNNING' } = {}) {
	return {
		status,
		turns: 20,
		turn: { number: 4, phase, doneBidding: [], doneInvesting },
		players: [
			{ email: 'vega@example.com', corporationName: 'Vega Lines' },
			{ email: ME, corporationName: 'Orion Freight' },
			{ email: 'kessler@example.com', corporationName: 'Kessler Cargo' },
		],
	};
}

describe('isInvesting', () => {
	it('is true only for a running game in Investment', () => {
		assert.equal(isInvesting(game()), true);
		assert.equal(isInvesting(game({ phase: 'TRADING' })), false);
		assert.equal(isInvesting(game({ status: 'FINISHED' })), false);
	});
});

describe('isDoneInvesting', () => {
	it('is true only for a player in the done list during Investment', () => {
		assert.equal(isDoneInvesting(game({ doneInvesting: [ME] }), ME), true);
		assert.equal(isDoneInvesting(game({ doneInvesting: ['vega@example.com'] }), ME), false);
		assert.equal(isDoneInvesting(game({ phase: 'BIDDING', doneInvesting: [ME] }), ME), false);
	});
});

describe('waitingForInvesting (E06-S01-3)', () => {
	it('names you first, then the others in join order, while nobody is done', () => {
		assert.deepEqual(waitingForInvesting(game(), ME), ['you', 'Vega Lines', 'Kessler Cargo']);
	});

	it('leaves out players who signalled done, you included', () => {
		assert.deepEqual(waitingForInvesting(game({ doneInvesting: ['vega@example.com'] }), ME), ['you', 'Kessler Cargo']);
		assert.deepEqual(waitingForInvesting(game({ doneInvesting: [ME] }), ME), ['Vega Lines', 'Kessler Cargo']);
	});

	it('waits for nobody outside Investment', () => {
		assert.deepEqual(waitingForInvesting(game({ phase: 'TRADING' }), ME), []);
	});
});

describe('the top bar during Investment', () => {
	it('names who the game is waiting for', () => {
		assert.equal(phaseText(game(), ME), 'Turn 4 of 20 · Investment — waiting for you, Vega Lines, Kessler Cargo');
		assert.equal(phaseText(game({ doneInvesting: [ME] }), ME), 'Turn 4 of 20 · Investment — waiting for Vega Lines, Kessler Cargo');
	});
});

describe('done investing messages', () => {
	it('says nothing more while others still invest, or that your signal closed Investment', () => {
		assert.equal(doneInvestingAcceptedText(game()), '');
		assert.equal(doneInvestingAcceptedText(game({ phase: 'BIDDING' })), INVESTMENT_CLOSED_BY_YOU);
	});

	it('words a close that raced the click as already closed (A04)', () => {
		assert.equal(isInvestmentClosedRefusal(409, "Investment isn't open."), true);
		assert.equal(isInvestmentClosedRefusal(409, 'This game is over'), false);
		assert.equal(doneInvestingRefusedText(409, "Investment isn't open.", 'fallback'), ALREADY_CLOSED_INVESTING);
	});

	it('shows the server message otherwise, or the fallback', () => {
		assert.equal(doneInvestingRefusedText(409, 'This game is over', 'fallback'), 'This game is over');
		assert.equal(doneInvestingRefusedText(500, '', 'fallback'), 'fallback');
	});
});
