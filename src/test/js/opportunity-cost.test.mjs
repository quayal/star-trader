import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { opportunityCost } from '../../main/resources/static/js/opportunities.js';

const rules = {
	hulls: [{ id: 'DAGGER', name: 'Dagger', price: 250, crew: 1 }],
	modules: [
		{ id: 'ARSENAL', name: 'Arsenal', price: 100, crew: 1 },
		{ id: 'DOGFIGHT', name: 'Dogfight', price: 30, crew: 0 },
		{ id: 'CARGO', name: 'Cargo', price: 4, crew: 0 },
	],
	crewClasses: [{ id: 'A', pricePerCrew: 20 }, { id: 'B', pricePerCrew: 10 }],
};

const game = (money, ships = []) => ({ you: { money, ships } });
const ship = { kind: 'MILITARY_SHIP' };

describe('what taking an opportunity costs, live (R112, R113)', () => {
	it('prices illegal goods and furs by the unit', () => {
		const form = { kind: 'ILLEGAL_GOODS', price: 10 };

		assert.deepEqual(opportunityCost(game(100), rules, form, { units: '4' }), { total: 40, refund: 0, affordable: true, text: '4 × 10 = 40 HT' });
		assert.equal(opportunityCost(game(30), rules, form, { units: '4' }).affordable, false);
		assert.equal(opportunityCost(game(100), rules, form, { units: '' }), null);
		assert.equal(opportunityCost(game(100), rules, form, { units: '2.5' }), null);
	});

	it('prices a new military ship as hull, modules and crew, and follows each change', () => {
		const choice = { hull: 'DAGGER', crew: 'B', modules: [] };

		assert.equal(opportunityCost(game(300), rules, ship, choice).text, 'Hull 250 + modules 0 + crew 1 × 10 = 260 HT');
		assert.equal(opportunityCost(game(300), rules, ship, { ...choice, modules: ['ARSENAL', 'DOGFIGHT'] }).total, 250 + 130 + 2 * 10);
		assert.equal(opportunityCost(game(300), rules, ship, { ...choice, crew: 'A' }).total, 270);
	});

	it('says a ship is too dear for the viewer’s money', () => {
		assert.equal(opportunityCost(game(130), rules, ship, { hull: 'DAGGER', crew: 'B', modules: [] }).affordable, false);
		assert.equal(opportunityCost(game(260), rules, ship, { hull: 'DAGGER', crew: 'B', modules: [] }).affordable, true);
	});

	it('prices modules alone at the chosen ship’s crew class, less what taking modules off pays back', () => {
		const form = { kind: 'MILITARY_MODULES' };
		const own = game(100, [{ name: 'Wayfarer', crewClass: 'A' }]);

		const cost = opportunityCost(own, rules, form, { ship: 'Wayfarer', modules: ['ARSENAL'], removed: [] });
		assert.deepEqual(cost, { total: 120, refund: 0, affordable: false, text: 'Modules 100 + crew 20 = 120 HT' });
		const refunded = opportunityCost(own, rules, form, { ship: 'Wayfarer', modules: ['ARSENAL'], removed: ['ARSENAL', 'CARGO'] });
		assert.equal(refunded.refund, 52);
		assert.equal(refunded.affordable, true); // 120 − 52 ≤ 100
		assert.equal(refunded.text, 'Modules 100 + crew 20 = 120 HT, 52 HT back');
	});

	it('prices modules alone for a military-ship opportunity with no hull chosen', () => {
		const own = game(500, [{ name: 'Wayfarer', crewClass: 'B' }]);

		assert.equal(opportunityCost(own, rules, ship, { hull: null, ship: 'Wayfarer', modules: ['DOGFIGHT'], crew: 'B' }).total, 30);
	});

	it('prices nothing while the choice is incomplete or the opportunity is free', () => {
		assert.equal(opportunityCost(game(100), rules, ship, { hull: 'DAGGER', crew: 'Z', modules: [] }), null);
		assert.equal(opportunityCost(game(100), rules, { kind: 'MILITARY_MODULES' }, { ship: 'Nobody', modules: ['ARSENAL'] }), null);
		assert.equal(opportunityCost(game(100), rules, { kind: 'MILITARY_MODULES' }, { ship: null, modules: [] }), null);
		assert.equal(opportunityCost(game(100), rules, { kind: 'EXPEDITION' }, {}), null);
		assert.equal(opportunityCost(game(100), null, ship, { hull: 'DAGGER', crew: 'B', modules: [] }), null);
	});
});
