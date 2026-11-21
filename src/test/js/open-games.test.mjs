import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { creatorText, openGameRow, requestOutcome } from '../../main/resources/static/js/open-games.js';

function game(overrides = {}) {
	return {
		id: 'g1',
		turns: 20,
		creator: { name: 'Ada', corporationName: 'Orion Freight' },
		corporations: ['Orion Freight', 'Vega Lines'],
		players: 2,
		maxPlayers: 6,
		standing: 'NONE',
		...overrides,
	};
}

describe('openGameRow', () => {
	it('names the creator and says how long the game is and who is in', () => {
		const row = openGameRow(game());

		assert.equal(row.title, 'Orion Freight (Ada)');
		assert.equal(row.details, '20 turns · 2 of 6 players · Orion Freight, Vega Lines');
		assert.equal(row.href, '/#game/g1');
		assert.equal(row.action, 'ask');
		assert.equal(row.badge, null);
	});

	it('offers to open a game the viewer is in, and to cancel a request', () => {
		assert.equal(openGameRow(game({ standing: 'IN' })).action, 'open');
		const asked = openGameRow(game({ standing: 'ASKED' }));
		assert.equal(asked.action, 'cancel');
		assert.deepEqual(asked.badge, { text: 'Waiting for Orion Freight to accept', tone: 'warn' });
	});

	it('offers nothing on a full game the viewer is not in', () => {
		const row = openGameRow(game({ players: 6 }));

		assert.equal(row.action, null);
		assert.equal(row.badge.text, 'Full');
	});
});

describe('creatorText', () => {
	it('puts the corporation first', () => {
		assert.equal(creatorText(game()), 'Orion Freight (Ada)');
	});
});

describe('requestOutcome', () => {
	it('opens a game whose creator accepted the request', () => {
		assert.deepEqual(requestOutcome([game({ standing: 'ASKED' })], [game({ standing: 'IN' })]), { accepted: 'g1', message: '' });
	});

	it('says when a request was declined or the game left the list', () => {
		const before = [game({ standing: 'ASKED' }), game({ id: 'g2', standing: 'ASKED', creator: { name: 'Bo', corporationName: 'Bo Haulage' } })];

		assert.deepEqual(requestOutcome(before, [game({ standing: 'NONE' })]), {
			accepted: null,
			message: "Orion Freight declined your request to join. Bo Haulage's game is no longer open.",
		});
	});

	it('says nothing while requests wait, or about games the viewer never asked to join', () => {
		assert.deepEqual(requestOutcome([game({ standing: 'ASKED' }), game({ id: 'g3' })], [game({ standing: 'ASKED' })]), {
			accepted: null,
			message: '',
		});
	});
});
