import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { deliveriesDue, expeditionsDue, opportunitiesInAction, opportunityForms, passengerForms, passengersAboard, specialSales } from '../../main/resources/static/js/opportunities.js';

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
		assert.equal(form.takeable, true);
		// Playtest 2026-11-21: with no ship where it's taken, Take isn't offered as if it could work.
		assert.equal(opportunityForms(game([weapons], [], [{ ...positions[0], location: 'PORT' }]))[0].takeable, false);
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

	it('shows each ship\'s free module room and the modules that could come off to make it (creator, 2026-11-21)', () => {
		const comm = {
			slot: 1, learned: true, taken: false, details: 'Opportunity: Battle comm on Gamma Leporis\'s Planet.',
			terms: { kind: 'MILITARY_MODULES', buyAt: 'GAMMA_LEPORIS', hulls: [], modules: ['BATTLE_COMM'], hullOptional: false },
		};
		const ships = [{
			name: 'Wayfarer',
			stats: { modulesUsed: 5, moduleCapacity: 5 },
			modules: [
				{ id: 'CARGO', name: 'Cargo', count: 2, resaleValue: 2, takesModuleCapacity: true },
				{ id: 'SAFE_JUMP', name: 'Safe jump', count: 1, resaleValue: 5, takesModuleCapacity: false },
				{ id: 'LIGHT_WEAPONS', name: 'Light weapons', count: 1, resaleValue: 8, takesModuleCapacity: true },
			],
		}];
		const [form] = opportunityForms(game([comm], ships, [{ name: 'Wayfarer', system: 'GAMMA_LEPORIS', location: 'PLANET' }]));

		assert.deepEqual(form.room, {
			Wayfarer: {
				free: 0,
				removable: [
					{ id: 'CARGO', name: 'Cargo', resaleValue: 2 },
					{ id: 'CARGO', name: 'Cargo', resaleValue: 2 },
					{ id: 'LIGHT_WEAPONS', name: 'Light weapons', resaleValue: 8 },
				],
			},
		});
	});
});

describe('opportunities in action (creator, 2026-11-23)', () => {
	it('lists goods aboard, deliveries and expeditions with what comes next', () => {
		const game = {
			board: [{ id: 'MU_HERCULIS', name: 'Mu Herculis' }, { id: 'TAU_CETI', name: 'Tau Ceti' }],
			players: [{ id: 'p1', ships: [{ name: 'Wayfarer', system: 'MU_HERCULIS', location: 'PORT' }, { name: 'Lantern', system: 'TAU_CETI', location: 'PLANET' }] }],
			you: {
				id: 'p1',
				ships: [
					{ name: 'Wayfarer', specialLots: [{ goods: 'FURS', name: 'furs', units: 6, sellAt: 'BETA_HYDRI', sellAtName: 'Beta Hydri', illegal: false }], deliveryJob: null },
					{ name: 'Lantern', specialLots: [{ goods: 'WEAPONS', name: 'weapons', units: 4, sellAt: 'GAMMA_LEPORIS', sellAtName: 'Gamma Leporis', illegal: true }],
						deliveryJob: 'transport exotic plants from Gamma Leporis to Epsilon Eridani (150 HT)' },
				],
				expeditions: [{ ship: 'Corvid', returnTurn: 7, due: false }],
			},
		};

		assert.deepEqual(opportunitiesInAction(game), [
			{ ship: 'Wayfarer', where: 'Mu Herculis Port', what: '6 furs', next: 'Sell in Beta Hydri\'s Port' },
			{ ship: 'Lantern', where: 'Tau Ceti Planet', what: '4 weapons (illegal)', next: 'Sell on Gamma Leporis\'s Planet, on the black market' },
			{ ship: 'Lantern', where: 'Tau Ceti Planet', what: 'Delivery: transport exotic plants from Gamma Leporis to Epsilon Eridani (150 HT)', next: 'Deliver in the destination Port' },
			{ ship: 'Corvid', where: 'on the expedition', what: 'Scientific expedition', next: 'Back in turn 7' },
		]);
		assert.deepEqual(opportunitiesInAction({ players: [], you: { ships: [] } }), []);
	});
});

describe('deliveries due (R121)', () => {
	it('offers a Deliver button only for ships the server says can deliver', () => {
		const game = { you: { ships: [
			{ name: 'Wayfarer', canDeliver: true, deliveryJob: 'transport exotic plants from Gamma Leporis to Epsilon Eridani (150 HT)' },
			{ name: 'Lantern', canDeliver: false, deliveryJob: 'transport an envoy and his team from Epsilon Eridani to Mu Herculis (200 HT)' },
			{ name: 'Corvid', canDeliver: false, deliveryJob: null },
		] } };

		assert.deepEqual(deliveriesDue(game), [
			{ ship: 'Wayfarer', label: 'Deliver with Wayfarer: transport exotic plants from Gamma Leporis to Epsilon Eridani (150 HT)' },
		]);
		assert.deepEqual(deliveriesDue({}), []);
	});
});

describe('passengers (R125)', () => {
	const game = {
		board: [{ id: 'TAU_CETI', name: 'Tau Ceti' }, { id: 'BETA_HYDRI', name: 'Beta Hydri' }, { id: 'MU_HERCULIS', name: 'Mu Herculis' }],
		players: [{ id: 'p1', ships: [
			{ name: 'Wayfarer', system: 'TAU_CETI', location: 'PORT' },
			{ name: 'Lantern', system: 'TAU_CETI', location: 'PORT' },
			{ name: 'Corvid', system: 'BETA_HYDRI', location: 'SPACE' },
		] }],
		passengerRoutes: [
			{ from: 'TAU_CETI', to: 'BETA_HYDRI', groups: 7, left: 4 },
			{ from: 'TAU_CETI', to: 'MU_HERCULIS', groups: 3, left: 0 },
			{ from: 'BETA_HYDRI', to: 'TAU_CETI', groups: 7, left: 7 },
		],
		you: { id: 'p1', ships: [
			{ name: 'Wayfarer', freeSeats: 3, passengerGroups: [] },
			{ name: 'Lantern', freeSeats: 0, passengerGroups: [] },
			{ name: 'Corvid', freeSeats: 2 },
		], passengerGroups: [{ ship: 'Corvid', to: 'TAU_CETI', toName: 'Tau Ceti', groups: 2 }] },
	};

	it('offers a form for each ship in a Port with a free seat, with the routes that still have groups', () => {
		assert.deepEqual(passengerForms(game), [
			{ ship: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', seats: 3, destinations: [{ id: 'BETA_HYDRI', name: 'Beta Hydri', left: 4 }] },
		]);
		assert.deepEqual(passengerForms({ players: [], you: { ships: [] } }), []);
	});

	it('lists passengers aboard in their own box, with what is owed if they are not delivered, not among the opportunities in action (creator, 2026-11-25)', () => {
		assert.deepEqual(passengersAboard(game), [
			{ ship: 'Corvid', where: 'Beta Hydri Space', what: '2 passenger groups for Tau Ceti', next: "The ship must be in Tau Ceti's Port, seats in working order, when the next Opportunity opens, or you pay 20 HT" },
		]);
		assert.deepEqual(opportunitiesInAction(game), []);
		assert.deepEqual(passengersAboard({ players: [], you: {} }), []);
	});
});
