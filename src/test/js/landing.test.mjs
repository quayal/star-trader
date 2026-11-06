import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { landedText, landingFor, landingHeading, landingOptions } from '../../main/resources/static/js/landing.js';

const ME = 'me@example.com';
const VEGA = 'vega@example.com';
const TAU_CETI = { id: 'TAU_CETI', name: 'Tau Ceti', portClass: 3, legalSystem: 2, police: 4, security: 1 };

function game({ landingShip = 'Wayfarer', doneJumping = [], phase = 'SHIP_JUMPS' } = {}) {
	return {
		status: 'RUNNING',
		turn: { number: 2, phase, initiative: [ME, VEGA], turnOrder: [ME, VEGA], jumpedShips: ['Wayfarer'], doneJumping, landingShip },
		players: [
			{ email: ME, corporationName: 'Orion Freight', ships: [{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SPACE' }] },
			{ email: VEGA, corporationName: 'Vega Lines', ships: [] },
		],
		board: [TAU_CETI],
	};
}

describe('landingFor', () => {
	it('is the ship you jumped and have not landed yet, with its system (S03-1, S03-6)', () => {
		const landing = landingFor(game(), ME);

		assert.equal(landing.ship.name, 'Wayfarer');
		assert.equal(landing.system, TAU_CETI);
	});

	it('is nothing for other players, with no ship waiting, or outside your slot', () => {
		assert.equal(landingFor(game(), VEGA), null);
		assert.equal(landingFor(game({ landingShip: null }), ME), null);
		assert.equal(landingFor(game({ phase: 'TRADING' }), ME), null);
	});
});

describe('landingHeading', () => {
	it('says where the ship arrived (S03-1)', () => {
		assert.equal(landingHeading({ ship: { name: 'Wayfarer' }, system: TAU_CETI }), 'Wayfarer arrived at Tau Ceti');
	});
});

describe('landingOptions', () => {
	it('lists port, planet and space with their R31 statistics, port first', () => {
		assert.deepEqual(landingOptions(TAU_CETI), [
			{ value: 'PORT', label: 'Land at the port (class 3, legal system 2)' },
			{ value: 'PLANET', label: 'Land on the planet (security 1)' },
			{ value: 'SPACE', label: 'Stay in space (police 4)' },
		]);
	});
});

describe('landedText', () => {
	it('says where the ship ended up', () => {
		assert.equal(landedText('Wayfarer', 'PORT'), 'Wayfarer landed at the port.');
		assert.equal(landedText('Wayfarer', 'PLANET'), 'Wayfarer landed on the planet.');
		assert.equal(landedText('Wayfarer', 'SPACE'), 'Wayfarer stays in space.');
	});
});
