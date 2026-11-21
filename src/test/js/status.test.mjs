import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { reputationEffect, statusItems } from '../../main/resources/static/js/status.js';

const game = (me = {}) => ({
	you: { id: 'me', money: 240 },
	players: [{ id: 'me', reputation: 27, reputationRecovery: 3, reputationBonus: 15, connections: { political: 2, business: 4, criminal: 1 }, ...me }],
});

describe('the status strip (playtest, 2026-11-21)', () => {
	it('shows money, reputation with its effect, and connections', () => {
		assert.deepEqual(statusItems(game(), 30), [
			{ label: 'Money', value: '240 HT', note: '' },
			{ label: 'Reputation', value: '27', note: '+3 next turn · +15 HT bonus' },
			{ label: 'Connections', value: 'POL 2 · BUSS 4 · CRIM 1', note: '' },
		]);
	});

	it('says from which level a bonus comes, and leaves out a recovery that won’t happen', () => {
		assert.equal(reputationEffect({ reputationRecovery: 0, reputationBonus: 0 }, 30), 'bonus from 30');
		assert.equal(reputationEffect({ reputationRecovery: 3, reputationBonus: 0 }, undefined), '+3 next turn');
	});

	it('is empty before the viewer has loaded', () => {
		assert.deepEqual(statusItems({ players: [] }, 30), []);
	});
});
