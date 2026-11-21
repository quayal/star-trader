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

	it('says all a player needs to take furs: how many, the cost, no cargo space and the sale (playtest, 2026-11-21)', () => {
		const furs = {
			slot: 5, learned: true, taken: false, details: 'Opportunity: buy furs in Tau Ceti\'s Port (10 HT) for sale in Gamma Leporis\'s Port (30 HT).',
			terms: { kind: 'FURS', price: 10, buyAt: 'TAU_CETI', sellAt: 'GAMMA_LEPORIS', hulls: [], modules: [], hullOptional: false, maxUnits: 10, hold: 'NONE', salePrice: 30 },
		};
		const rich = { ...game([furs]), you: { ...game([furs]).you, money: 300 } };
		const [form] = opportunityForms(rich);
		assert.deepEqual(form.facts, [
			'Buy 1 to 10 units at 10 HT each: you can afford 10 (100 HT).',
			'Furs need no cargo space.',
			'Sell them for 30 HT each in Gamma Leporis\'s Port, on your Opportunity turn there.',
		]);
		assert.equal(form.maxUnits, 10);
		assert.equal(form.defaultUnits, 10);
		const poor = { ...rich, you: { ...rich.you, money: 45 } };
		assert.equal(opportunityForms(poor)[0].facts[0], 'Buy 1 to 10 units at 10 HT each: you can afford 4 (40 HT).');
	});

	it('says what each unit of illegal goods takes aboard, and where it sells', () => {
		const withTerms = terms => ({ ...weapons, terms: { ...weapons.terms, price: 10, sellAt: 'GAMMA_LEPORIS', maxUnits: 10, ...terms } });
		const facts = terms => opportunityForms({ ...game([withTerms(terms)]), you: { ...game([]).you, tipOffs: [withTerms(terms)], money: 100 } })[0].facts;
		assert.equal(facts({ hold: 'CARGO_SPACE' })[1], 'Each unit takes a cargo space.');
		assert.equal(facts({ hold: 'PASSENGER_SEAT' })[1], 'Each unit takes a passenger seat.');
		assert.equal(facts({ hold: 'CARGO_SPACE' })[2], 'Sell them on Gamma Leporis\'s black market (on its Planet), at a price rolled on a d6.');
	});
});
