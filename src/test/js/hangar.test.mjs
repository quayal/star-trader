import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { HANGAR_TEXT } from '../../main/resources/static/js/board.js';
import {
	ESCAPE_STAYS,
	hangarAnsweredText,
	hangarFor,
	hangarHeading,
	hangarNoLabel,
	hangarQuestion,
	hangarYesLabel,
} from '../../main/resources/static/js/hangar.js';

const ME = 'me';
const VEGA = 'vega';
const TAU_CETI = { id: 'TAU_CETI', name: 'Tau Ceti', hangar: true };

function game({ hangarShip = 'Wayfarer', hangar = false, doneJumping = [], interception = null } = {}) {
	return {
		status: 'RUNNING',
		turn: { number: 2, phase: 'JUMPS', initiative: [ME, VEGA], turnOrder: [ME, VEGA], doneJumping, hangarShip, interception, battle: null },
		players: [
			{ id: ME, corporationName: 'Orion Freight', ships: [{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT', hangar }] },
			{ id: VEGA, corporationName: 'Vega Lines', ships: [] },
		],
		board: [TAU_CETI],
	};
}

describe('hangarFor', () => {
	it('is the ship being asked about, with its system, for the player whose slot it is (R127)', () => {
		const hangar = hangarFor(game(), ME);

		assert.equal(hangar.ship.name, 'Wayfarer');
		assert.equal(hangar.system, TAU_CETI);
		assert.equal(hangar.renewal, false);
	});

	it('is a renewal when the ship is already in the hangar', () => {
		assert.equal(hangarFor(game({ hangar: true }), ME).renewal, true);
	});

	it('is nothing for other players, with no question open, or while a fight is decided', () => {
		assert.equal(hangarFor(game(), VEGA), null);
		assert.equal(hangarFor(game({ hangarShip: null }), ME), null);
		assert.equal(hangarFor(game({ interception: { ship: 'Meridian' } }), ME), null);
	});
});

describe('the dialog texts', () => {
	it('heads the dialog with the ship and the system', () => {
		assert.equal(hangarHeading(hangarFor(game(), ME)), 'Wayfarer: Parking Hangar at Tau Ceti');
	});

	it('asks to park a new arrival and to keep a parked ship, naming the fee and what it protects from', () => {
		assert.equal(hangarQuestion(hangarFor(game(), ME), 5), `Park Wayfarer in the hangar this turn? It costs 5 HT. ${HANGAR_TEXT}`);
		assert.equal(hangarQuestion(hangarFor(game({ hangar: true }), ME), 5), `Keep Wayfarer in the hangar for another turn? It costs 5 HT. ${HANGAR_TEXT}`);
	});

	it('labels the answers', () => {
		assert.equal(hangarYesLabel(5), 'Yes, pay 5 HT');
		assert.equal(hangarNoLabel(hangarFor(game(), ME)), 'No, thanks');
		assert.equal(hangarNoLabel(hangarFor(game({ hangar: true }), ME)), 'No, take it out');
		assert.equal(ESCAPE_STAYS, false);
	});

	it('announces the answer', () => {
		assert.equal(hangarAnsweredText('Wayfarer', false, true), 'Wayfarer is parked in the hangar.');
		assert.equal(hangarAnsweredText('Wayfarer', true, true), 'Wayfarer stays in the hangar.');
		assert.equal(hangarAnsweredText('Wayfarer', true, false), 'Wayfarer left the hangar.');
		assert.equal(hangarAnsweredText('Wayfarer', false, false), '');
	});
});
