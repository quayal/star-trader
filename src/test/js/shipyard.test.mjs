import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	BUY_CLOSED_TEXT,
	LAST_TURN_WARNING,
	NAME_CHARACTERS_ERROR,
	NAME_ERROR,
	SECOND_LAST_TURN_WARNING,
	HULL_COLUMNS,
	buyClosedText,
	canSuggest,
	fitCheck,
	hullRows,
	lateBuyWarning,
	moduleList,
	refitOptions,
	saleConfirmText,
	saleOutcome,
	saleResult,
	shipCost,
	soldText,
	suggestName,
	validateShipName,
} from '../../main/resources/static/js/shipyard.js';

const SHIPYARD = {
	shipyards: [{ system: 'TAU_CETI', systemName: 'Tau Ceti' }],
	hulls: [
		{ id: 'CLARINET', name: 'Clarinet', price: 160, moduleCapacity: 5, crew: 1, cargoSpaces: 0, weapons: 3, shields: 2, intercept: 2, repairCost: 20, aerodynamic: false },
		{ id: 'CORCO_GAMMA', name: 'Corco Gamma', price: 80, moduleCapacity: 2, crew: 1, cargoSpaces: 0, weapons: 2, shields: 1, intercept: 1, repairCost: 10, aerodynamic: true },
		{ id: 'LEVIATHAN', name: 'Leviathan', price: 250, moduleCapacity: 18, crew: 4, cargoSpaces: 6, weapons: 1, shields: 1, intercept: 1, repairCost: 20, aerodynamic: false },
	],
	modules: [
		{ id: 'SAFE_JUMP', name: 'Safe jump', price: 10, crew: 0, cargoSpaces: 0, weapons: 0, passengers: 0, takesModuleCapacity: false },
		{ id: 'CARGO', name: 'Cargo', price: 4, crew: 0, cargoSpaces: 2, weapons: 0, passengers: 0, takesModuleCapacity: true },
		{ id: 'LIGHT_WEAPONS', name: 'Light weapons', price: 17, crew: 0, cargoSpaces: 0, weapons: 3, passengers: 0, takesModuleCapacity: true },
		{ id: 'PASSENGER', name: 'Passenger', price: 15, crew: 1, cargoSpaces: 0, weapons: 0, passengers: 2, takesModuleCapacity: true },
	],
	crewClasses: [
		{ id: 'A', pricePerCrew: 20, jumpModifier: 5, interceptModifier: 1 },
		{ id: 'B', pricePerCrew: 10, jumpModifier: 3, interceptModifier: 0 },
		{ id: 'C', pricePerCrew: 6, jumpModifier: 1, interceptModifier: 0 },
		{ id: 'D', pricePerCrew: 2, jumpModifier: 0, interceptModifier: -1 },
	],
	freeNames: ['Spiral', 'Coral', 'Knight'],
};

const STARTING = { CARGO: '4', LIGHT_WEAPONS: '1', SAFE_JUMP: '1' };

function game({ cargo = {}, warehouse = null, turn = 3, turns = 20 } = {}) {
	return {
		turns,
		turn: { number: turn, phase: 'INVESTMENT' },
		players: [
			{ id: 'me', ships: [{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' }] },
			{ id: 'other', ships: [{ name: 'Meridian', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', location: 'PORT' }] },
		],
		you: {
			money: 300,
			ships: [{ name: 'Wayfarer', cargo, saleValue: 101 }],
			warehouses: warehouse ? [warehouse] : [],
			invest: { shipyard: SHIPYARD },
		},
	};
}

describe('hullRows', () => {
	it('lists every hull for sale with its characteristics under the columns, and marks the picked one (R51)', () => {
		const rows = hullRows(SHIPYARD, 'CORCO_GAMMA');

		assert.deepEqual(HULL_COLUMNS, ['Price', 'Modules', 'Cargo', 'Weapons', 'Shields', 'Intercept', 'Crew', 'Repair']);
		assert.deepEqual(rows.map(row => [row.id, row.selected]), [['CLARINET', false], ['CORCO_GAMMA', true], ['LEVIATHAN', false]]);
		assert.deepEqual(rows[2].cells, ['250 HT', '18', '6', '1', '1', '1', '4', '20 HT']);
	});

	it('names a hull\'s flags in words, never by colour alone (R56)', () => {
		const rows = hullRows(SHIPYARD, 'CLARINET');

		assert.deepEqual(rows[1].flags, ['Aerodynamic']);
		assert.deepEqual(rows[0].flags, []);
	});

	it('is empty when the viewer cannot buy now', () => {
		assert.deepEqual(hullRows(null, 'CLARINET'), []);
	});
});

describe('shipCost', () => {
	it('adds the hull, every module and the crew at its class (R61)', () => {
		const cost = shipCost(SHIPYARD, 'CLARINET', STARTING, 'B');
		assert.equal(cost.total, 213);
		assert.equal(cost.text, 'Hull 160 + modules 43 + crew 1 × 10 = 213 HT');
	});

	it('counts the crew of the hull and of every module', () => {
		const cost = shipCost(SHIPYARD, 'LEVIATHAN', { PASSENGER: '7' }, 'A');
		assert.equal(cost.crewCount, 11);
		assert.equal(cost.total, 250 + 105 + 220);
	});

	it('is null for a hull or class the shipyard does not sell', () => {
		assert.equal(shipCost(SHIPYARD, 'SWORD', {}, 'B'), null);
		assert.equal(shipCost(SHIPYARD, 'CLARINET', {}, 'Z'), null);
	});
});

describe('fitCheck', () => {
	it('counts every module but Safe jump against the hull (R52)', () => {
		assert.deepEqual(fitCheck(SHIPYARD, 'CLARINET', STARTING), { used: 5, capacity: 5, error: null });
	});

	it('refuses more modules than the hull holds, and a second Safe jump', () => {
		assert.equal(fitCheck(SHIPYARD, 'CORCO_GAMMA', { CARGO: '3' }).error, 'A Corco Gamma holds 2 modules, not 3.');
		assert.equal(fitCheck(SHIPYARD, 'CLARINET', { SAFE_JUMP: '2' }).error, 'A ship has at most one Safe jump module.');
	});

	it('refuses a count that is not a whole number of 0 or more', () => {
		assert.equal(fitCheck(SHIPYARD, 'CLARINET', { CARGO: '1.5' }).error, 'Enter a whole number of each module, 0 or more.');
		assert.equal(fitCheck(SHIPYARD, 'CLARINET', { CARGO: '-1' }).error, 'Enter a whole number of each module, 0 or more.');
	});
});

describe('moduleList', () => {
	it('sends one id per unit, in the shipyard order', () => {
		assert.deepEqual(moduleList(SHIPYARD, STARTING), ['SAFE_JUMP', 'CARGO', 'CARGO', 'CARGO', 'CARGO', 'LIGHT_WEAPONS']);
	});
});

describe('validateShipName', () => {
	it('trims the name and refuses an empty or too long one (R62)', () => {
		assert.deepEqual(validateShipName(game(), '  Sea Wolf '), { name: 'Sea Wolf' });
		assert.equal(validateShipName(game(), '   ').error, NAME_ERROR);
		assert.equal(validateShipName(game(), 'A'.repeat(21)).error, NAME_ERROR);
	});

	it('refuses a name that cannot be the ship address in a URL, as the server does', () => {
		for (const name of ['A/B', 'a\\b', '.', '..', '--']) {
			assert.equal(validateShipName(game(), name).error, NAME_CHARACTERS_ERROR, name);
		}
		assert.deepEqual(validateShipName(game(), "St. Jude's Ark-2"), { name: "St. Jude's Ark-2" });
		assert.deepEqual(validateShipName(game(), 'Łódź'), { name: 'Łódź' });
	});

	it('refuses a name any ship in the game has, ignoring case', () => {
		assert.equal(validateShipName(game(), 'meridian').error, "There's already a ship called meridian in this game.");
	});
});

describe('suggestName', () => {
	it('picks a free name other than the one in the field', () => {
		assert.equal(suggestName(SHIPYARD, 'Spiral', () => 0), 'Coral');
		assert.equal(suggestName(SHIPYARD, '', () => 0.99), 'Knight');
	});

	it('is null once every suggestion is taken', () => {
		assert.equal(suggestName({ ...SHIPYARD, freeNames: [] }, ''), null);
		assert.equal(canSuggest({ ...SHIPYARD, freeNames: ['Spiral'] }, 'Spiral'), false);
		assert.equal(canSuggest(SHIPYARD, 'Spiral'), true);
	});
});

describe('buyClosedText', () => {
	it('says nothing while the viewer may buy, so the form shows instead', () => {
		assert.equal(buyClosedText({ ...game(), status: 'RUNNING' }), '');
	});

	it('says the form only plans in any other phase, and buying opens in Investment', () => {
		const trading = { ...game(), status: 'RUNNING', turn: { number: 3, phase: 'TRADING' } };

		assert.equal(buyClosedText(trading), BUY_CLOSED_TEXT);
	});

	it('says nothing once the game is over: there is no next Investment', () => {
		const over = { ...game(), status: 'FINISHED', turn: { number: 20, phase: 'TRADING' } };

		assert.equal(buyClosedText(over), '');
	});
});

describe('lateBuyWarning', () => {
	it('warns in the last two turns, when the ship would never jump (R39, R63)', () => {
		assert.equal(lateBuyWarning(game({ turn: 18, turns: 20 })), '');
		assert.equal(lateBuyWarning(game({ turn: 19, turns: 20 })), SECOND_LAST_TURN_WARNING);
		assert.equal(lateBuyWarning(game({ turn: 20, turns: 20 })), LAST_TURN_WARNING);
	});
});

describe('selling a ship (R66)', () => {
	const warehouse = { system: 'TAU_CETI', cargoSpaces: 10, cargoUsed: 4, cargo: { POLY: 4 } };

	it('stores what fits in the warehouse there, in goods order, and loses the rest, as the server does', () => {
		const outcome = saleOutcome(game({ cargo: { FOOD: 9, ISO: 7, POLY: 2 }, warehouse }), 'me', 'Wayfarer');
		assert.deepEqual(outcome.stored, [{ goods: 'FOOD', units: 9 }, { goods: 'ISO', units: 6 }]);
		assert.deepEqual(outcome.lost, [{ goods: 'ISO', units: 1 }, { goods: 'POLY', units: 2 }]);
	});

	it('loses all of it without a warehouse there', () => {
		const outcome = saleOutcome(game({ cargo: { ISO: 3 } }), 'me', 'Wayfarer');
		assert.deepEqual(outcome.stored, []);
		assert.deepEqual(outcome.lost, [{ goods: 'ISO', units: 3 }]);
	});

	it('names the price and every unit that moves or is lost before and after', () => {
		const loaded = game({ cargo: { ISO: 7, POLY: 2 }, warehouse });
		assert.equal(saleConfirmText(loaded, 'me', loaded.you.ships[0]),
			'Sell Wayfarer for 101 HT? 6 ISO go to your warehouse at Tau Ceti. 1 ISO, 2 POLY are lost.');
		assert.equal(soldText(saleOutcome(loaded, 'me', 'Wayfarer'), 'Wayfarer', 101),
			'Sold Wayfarer for 101 HT. 6 ISO went to your warehouse at Tau Ceti. 1 ISO, 2 POLY were lost.');
	});

	it('reads what was stored from the server answer, not from the prediction', () => {
		const before = game({ cargo: { ISO: 7, POLY: 2 }, warehouse });
		const after = game({ warehouse: { ...warehouse, cargo: { POLY: 4, ISO: 5 } } });
		const result = saleResult(before, after, 'me', 'Wayfarer');
		assert.deepEqual(result.stored, [{ goods: 'ISO', units: 5 }]);
		assert.deepEqual(result.lost, [{ goods: 'ISO', units: 2 }, { goods: 'POLY', units: 2 }]);
	});

	it('says only the price for an empty ship', () => {
		const empty = game();
		assert.equal(saleConfirmText(empty, 'me', empty.you.ships[0]), 'Sell Wayfarer for 101 HT?');
	});

	it('says the cargo is lost with the ship when there is no warehouse', () => {
		const loaded = game({ cargo: { ISO: 1 } });
		assert.equal(saleConfirmText(loaded, 'me', loaded.you.ships[0]), 'Sell Wayfarer for 101 HT? 1 ISO is lost with it.');
	});
});

describe('refitOptions (R65)', () => {
	const ship = {
		name: 'Wayfarer',
		crewClass: 'B',
		canRefit: true,
		stats: { modulesUsed: 4, moduleCapacity: 5, crewNeeded: 2 },
		modules: [{ id: 'CARGO', name: 'Cargo', count: 4, resaleValue: 2 }],
	};

	it('offers every other crew class, at its price for each crew member the ship needs (R89)', () => {
		assert.deepEqual(refitOptions(SHIPYARD, ship).crew, [
			{ id: 'A', cost: 40, jumpModifier: 5, interceptModifier: 1 },
			{ id: 'C', cost: 12, jumpModifier: 1, interceptModifier: 0 },
			{ id: 'D', cost: 4, jumpModifier: 0, interceptModifier: -1 },
		]);
		assert.deepEqual(refitOptions(SHIPYARD, { ...ship, canRefit: false }).crew, []);
	});

	it('offers the modules that still fit, each at its price plus crew at the ship class', () => {
		const { add } = refitOptions(SHIPYARD, ship);
		assert.deepEqual(add.map(option => [option.id, option.cost]), [['SAFE_JUMP', 10], ['CARGO', 4], ['LIGHT_WEAPONS', 17], ['PASSENGER', 25]]);
	});

	it('offers no module that takes a slot on a full ship, and no second Safe jump', () => {
		const full = { ...ship, stats: { modulesUsed: 5, moduleCapacity: 5 }, modules: [...ship.modules, { id: 'SAFE_JUMP', name: 'Safe jump', count: 1, resaleValue: 5 }] };
		assert.deepEqual(refitOptions(SHIPYARD, full).add, []);
	});

	it('shows what the server pays back for a removed module, even one the shipyard does not sell', () => {
		const withArsenal = { ...ship, modules: [{ id: 'ARSENAL', name: 'Arsenal', count: 1, resaleValue: 50 }] };
		assert.deepEqual(refitOptions(SHIPYARD, withArsenal).remove, [{ id: 'ARSENAL', name: 'Arsenal', refund: 50 }]);
	});

	it('offers nothing away from a shipyard', () => {
		assert.deepEqual(refitOptions(SHIPYARD, { ...ship, canRefit: false }), { add: [], remove: [], crew: [] });
	});
});
