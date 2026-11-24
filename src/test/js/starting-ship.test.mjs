import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	MAX_NAME_LENGTH,
	NAME_LENGTH_TEXT,
	isChoosingShip,
	portChoices,
	suggestedName,
	validateStartingShip,
} from '../../main/resources/static/js/starting-ship.js';

function game({ number = 1, phase = 'BIDDING', ships = [], freeNames = ['Wayfarer', 'Lantern'], status = 'RUNNING' } = {}) {
	return {
		status,
		turn: { number, phase },
		you: { id: 'p1', ships, invest: { shipyard: { freeNames } } },
		board: [{ id: 'TAU_CETI', name: 'Tau Ceti' }, { id: 'MU_HERCULIS', name: 'Mu Herculis' }],
	};
}

describe('choosing the starting ship (R132)', () => {
	it('shows the form in turn 1’s Bidding only while the viewer has no ship', () => {
		assert.equal(isChoosingShip(game()), true);
		assert.equal(isChoosingShip(game({ ships: [{ name: 'Wayfarer' }] })), false);
		assert.equal(isChoosingShip(game({ number: 2 })), false);
		assert.equal(isChoosingShip(game({ phase: 'JUMPS' })), false);
		assert.equal(isChoosingShip({ ...game(), you: null }), false);
		assert.equal(isChoosingShip(game({ status: 'LOBBY' })), false);
	});

	it('suggests the first free name and lists every Port', () => {
		assert.equal(suggestedName(game()), 'Wayfarer');
		assert.equal(suggestedName(game({ freeNames: [] })), '');
		assert.deepEqual(portChoices(game()), [{ id: 'TAU_CETI', name: 'Tau Ceti' }, { id: 'MU_HERCULIS', name: 'Mu Herculis' }]);
	});

	it('sends a trimmed name and a Port, and says what the server would refuse', () => {
		assert.deepEqual(validateStartingShip('  Nimbus ', 'TAU_CETI'), { placement: { name: 'Nimbus', system: 'TAU_CETI' } });
		assert.equal(validateStartingShip('   ', 'TAU_CETI').error, NAME_LENGTH_TEXT);
		assert.equal(validateStartingShip('x'.repeat(MAX_NAME_LENGTH + 1), 'TAU_CETI').error, NAME_LENGTH_TEXT);
		assert.match(validateStartingShip('Nimbus', '').error, /Choose the system/);
	});
});
