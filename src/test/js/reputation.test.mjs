import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { BOUGHT_TEXT, myReputation, reputationPurchase, reputationText } from '../../main/resources/static/js/reputation.js';
import { corporationCards } from '../../main/resources/static/js/corporations.js';

/** The viewer in Investment, may buy, with [money] HT and reputation [reputation]. */
function investing({ money = 300, reputation = 20, boughtReputation = false } = {}) {
	return {
		status: 'RUNNING',
		turn: { number: 2, phase: 'INVESTMENT' },
		players: [{ id: 'p1', corporationName: 'Orion Freight', name: 'Ada', reputation }, { id: 'p2', corporationName: 'Vega Lines', name: 'Grace', reputation: 31 }],
		you: { id: 'p1', money, invest: { reputationCost: 20, boughtReputation } },
	};
}

describe('reputation (E13)', () => {
	it('shows every player\'s reputation out of 40, a missing one as the start value (E13-S01-1)', () => {
		assert.equal(reputationText(27), '27 / 40');
		assert.equal(reputationText(undefined), '20 / 40');
		assert.deepEqual(corporationCards(investing(), 'p1').map(card => card.reputation), ['20 / 40', '31 / 40']);
	});

	it('offers buying 1d6 for 20 HT once per Investment (E13-S03)', () => {
		const offer = reputationPurchase(investing());

		assert.equal(myReputation(investing()), 20);
		assert.deepEqual(offer, { intro: 'Your reputation is 20 / 40. Once per Investment, buy 1d6 more for 20 HT.', cost: 20, canBuy: true, reason: '' });
	});

	it('says why buying is closed: already bought, at 40, or too little money', () => {
		assert.equal(reputationPurchase(investing({ boughtReputation: true })).reason, BOUGHT_TEXT);
		assert.equal(reputationPurchase(investing({ reputation: 40 })).reason, 'Your reputation is already at 40.');
		assert.equal(reputationPurchase(investing({ money: 15 })).reason, 'Buying reputation costs 20 HT; you have 15 HT.');
		assert.equal(reputationPurchase({ ...investing(), you: { id: 'p1', money: 300 } }), null);
	});
});
