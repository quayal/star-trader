import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { expeditionsDue, opportunityForms, specialSales } from '../../main/resources/static/js/opportunities.js';

const ME = 'me';

function game(tipOffs, ships = [], positions = []) {
	return {
		board: [{ id: 'TAU_CETI', name: 'Tau Ceti' }, { id: 'GAMMA_LEPORIS', name: 'Gamma Leporis' }],
		players: [{ id: ME, ships: positions }],
		you: { id: ME, tipOffs, ships, expeditions: [{ ship: 'Lantern', returnTurn: 4, due: true }, { ship: 'Spiral', returnTurn: 6, due: false }] },
	};
}

describe('the Opportunities card (R111–R114)', () => {
	const weapons = {
		slot: 2, learned: true, taken: false, details: 'Opportunity: buy weapons on Tau Ceti\'s Planet (10 HT) for sale on Gamma Leporis\'s Planet.',
		terms: { kind: 'ILLEGAL_GOODS', buyAt: 'TAU_CETI', hulls: [], modules: [], hullOptional: false },
	};

	it('offers a learned, untaken opportunity to the ships that can take it', () => {
		const positions = [{ name: 'Wayfarer', system: 'TAU_CETI', location: 'PLANET' }, { name: 'Lantern', system: 'TAU_CETI', location: 'PORT' }];
		const [form] = opportunityForms(game([weapons], [], positions));

		assert.equal(form.title, 'buy weapons on Tau Ceti\'s Planet (10 HT) for sale on Gamma Leporis\'s Planet.');
		assert.deepEqual(form.ships, ['Wayfarer']);
		assert.equal(form.units, true);
		assert.deepEqual(opportunityForms(game([{ ...weapons, taken: true }])), []);
	});

	it('offers a sale where a ship holds goods bound there, and the expeditions due back', () => {
		const ships = [{ name: 'Wayfarer', specialLots: [{ goods: 'WEAPONS', name: 'weapons', units: 4, sellAt: 'GAMMA_LEPORIS', sellAtName: 'Gamma Leporis', illegal: true }] }];
		const there = [{ name: 'Wayfarer', system: 'GAMMA_LEPORIS', location: 'PLANET' }];

		assert.deepEqual(specialSales(game([], ships, there)), [
			{ kind: 'BLACK_MARKET', system: 'GAMMA_LEPORIS', goods: 'WEAPONS', units: 4, label: 'Sell 4 weapons on the black market at Gamma Leporis' },
		]);
		assert.deepEqual(specialSales(game([], ships, [{ ...there[0], location: 'PORT' }])), []);
		assert.deepEqual(expeditionsDue(game([])).map(e => e.ship), ['Lantern']);
	});
});
