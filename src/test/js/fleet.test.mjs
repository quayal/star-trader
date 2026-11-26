import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	AERODYNAMIC_LABEL,
	MILITARY_LABEL,
	MODULE_COLUMNS,
	NO_CARGO_SPACE_TEXT,
	NO_SHIPS_TEXT,
	cargoRows,
	flagLabels,
	fleetShips,
	moduleRows,
	statRows,
	whereText,
} from '../../main/resources/static/js/fleet.js';

const cargoModule = { name: 'Cargo', count: 4, weapons: 0, rocketWeapons: 0, crew: 0, cargoSpaces: 2, passengers: 0, military: false };
const lightWeapons = { name: 'Light weapons', count: 1, weapons: 3, rocketWeapons: 5, crew: 0, cargoSpaces: 0, passengers: 0, military: false };
const arsenal = { name: 'Arsenal', count: 1, weapons: 7, rocketWeapons: 14, crew: 1, cargoSpaces: 0, passengers: 0, military: true };

/** A starting ship as `you.ships` sends it (R55). */
const wayfarer = {
	name: 'Wayfarer',
	cargo: { CHIPS: 12, ISO: 3, POLY: 4 },
	cargoUsed: 7,
	cargoSpaces: 8,
	hull: { name: 'Clarinet', military: false, aerodynamic: false },
	modules: [cargoModule, lightWeapons],
	stats: { weapons: 6, rocketWeapons: 5, shields: 2, intercept: 2, crewNeeded: 1, passengers: 0, modulesUsed: 5, moduleCapacity: 5 },
};
const dagger = {
	name: 'Lantern',
	cargo: { FOOD: 2 },
	cargoUsed: 0,
	cargoSpaces: 0,
	hull: { name: 'Dagger', military: true, aerodynamic: true },
	modules: [arsenal],
	stats: { weapons: 12, rocketWeapons: 14, shields: 4, intercept: 2, crewNeeded: 2, passengers: 0, modulesUsed: 1, moduleCapacity: 1 },
};

const game = {
	players: [
		{
			id: 'ada',
			ships: [
				{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' },
				{ name: 'Lantern', system: 'MU_HERCULIS', systemName: 'Mu Herculis', location: 'SPACE' },
			],
		},
		{ id: 'grace', ships: [{ name: 'Meridian', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PLANET' }] },
	],
	you: { ships: [wayfarer, dagger] },
};

describe('fleetShips', () => {
	it('says when a ship in the yard leaves it, and shows its crew class (E09-S06-3, R67)', () => {
		const yard = {
			players: [{ id: 'ada', ships: [{ name: 'Spiral', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SHIPYARD' }] }],
			you: { ships: [{ ...wayfarer, name: 'Spiral', crewClass: 'D', leavesYardOnTurn: 3 }] },
		};

		const [spiral] = fleetShips(yard, 'ada');

		assert.equal(spiral.where, 'Tau Ceti · in the shipyard, leaves it in turn 3’s Investment');
		assert.deepEqual(spiral.stats.at(-1), { label: 'Crew class', value: 'D' });
		assert.equal(fleetShips({ ...yard, turns: 2 }, 'ada')[0].where, 'Tau Ceti · in the shipyard until the game ends');
	});

	it('lists my agents aboard each ship with what they do (R103)', () => {
		const withAgents = { ...game, you: { ...game.you, agents: [{ name: 'Silent', effect: 'Foils sabotage', ship: 'Lantern' }] } };

		assert.deepEqual(fleetShips(withAgents, 'ada').map(ship => [ship.name, ship.agents]), [
			['Wayfarer', []],
			['Lantern', [{ name: 'Silent', effect: 'Foils sabotage' }]],
		]);
	});

	it('lists only my ships, each with its hull, modules, stats, position and cargo (S03-2, S03-3, S03-5)', () => {
		const ships = fleetShips(game, 'ada');

		assert.deepEqual(ships.map(ship => ship.name), ['Wayfarer', 'Lantern']);
		assert.deepEqual(ships[0], {
			name: 'Wayfarer',
			systemId: 'TAU_CETI',
			where: 'Tau Ceti · Port',
			hull: { name: 'Clarinet', flags: [] },
			modules: [
				{ name: 'Cargo', flags: [], cells: ['× 4', '0', '0', '0', '2', '0'] },
				{ name: 'Light weapons', flags: [], cells: ['× 1', '3', '5', '0', '0', '0'] },
			],
			stats: [
				{ label: 'Weapons', value: '6' },
				{ label: 'Rockets', value: '5' },
				{ label: 'Shields', value: '2' },
				{ label: 'Intercept', value: '2' },
				{ label: 'Crew needed', value: '1' },
				{ label: 'Passengers', value: '0' },
				{ label: 'Modules', value: '5 / 5' },
			],
			agents: [],
			cargo: {
				used: 7,
				spaces: 8,
				text: '7 / 8 spaces',
				fill: '88%',
				goods: [{ goods: 'POLY', units: 4 }, { goods: 'ISO', units: 3 }, { goods: 'CHIPS', units: 12 }],
				note: null,
			},
		});
	});

	it('says a ship with no cargo spaces can carry no POLY or ISO, and still lists what it holds (S03-5)', () => {
		const lantern = fleetShips(game, 'ada')[1];

		assert.equal(lantern.cargo.note, NO_CARGO_SPACE_TEXT);
		assert.equal(lantern.cargo.note, 'No cargo space — fit Cargo modules to carry POLY or ISO');
		assert.equal(lantern.cargo.text, '0 / 0 spaces');
		assert.equal(lantern.cargo.fill, '0%');
		assert.deepEqual(lantern.cargo.goods, [{ goods: 'FOOD', units: 2 }]);
	});

	it('labels a military and aerodynamic hull and a military module in words (S03-4)', () => {
		const lantern = fleetShips(game, 'ada')[1];

		assert.deepEqual(lantern.hull, { name: 'Dagger', flags: ['Military: illegal to possess', 'Aerodynamic: easier landing'] });
		assert.deepEqual(lantern.modules[0].flags, [MILITARY_LABEL]);
		assert.equal(lantern.where, 'Mu Herculis · in Space');
	});

	it('never fills the meter past full for a ship holding more than its spaces', () => {
		const over = { ...game, you: { ships: [{ ...wayfarer, cargoUsed: 10 }] } };

		assert.equal(fleetShips(over, 'ada')[0].cargo.fill, '100%');
	});

	it('is empty for a player with no ships, and for someone who is not in the game (S03-8)', () => {
		assert.deepEqual(fleetShips({ players: [{ id: 'ada', ships: [] }], you: { ships: [] } }, 'ada'), []);
		assert.deepEqual(fleetShips(game, 'stranger'), []);
		assert.deepEqual(fleetShips({ ...game, you: null }, 'ada'), []);
		assert.equal(NO_SHIPS_TEXT, 'You have no ships');
	});

	it("shows nothing of another player's ships (S03-6)", () => {
		assert.deepEqual(fleetShips(game, 'grace'), []);
	});
});

describe('the parts', () => {
	it('names the columns of the module rows', () => {
		assert.equal(MODULE_COLUMNS.length - 1, moduleRows([cargoModule])[0].cells.length);
	});

	it('has no rows for a hull with no modules', () => {
		assert.deepEqual(moduleRows([]), []);
		assert.deepEqual(moduleRows(undefined), []);
	});

	it('labels nothing that has no flag', () => {
		assert.deepEqual(flagLabels({ military: false }), []);
		assert.deepEqual(flagLabels({ military: false, aerodynamic: true }), [AERODYNAMIC_LABEL]);
	});

	it('leaves out goods a ship carries none of', () => {
		assert.deepEqual(cargoRows({ POLY: 0, FOOD: 1 }), [{ goods: 'FOOD', units: 1 }]);
		assert.deepEqual(cargoRows(undefined), []);
	});

	it('names a location it does not know by its id', () => {
		assert.equal(whereText({ systemName: 'Tau Ceti', location: 'PLANET' }), 'Tau Ceti · Planet');
		assert.equal(whereText({ systemName: 'Tau Ceti', location: 'ORBIT' }), 'Tau Ceti · ORBIT');
	});

	it('shows modules used of the capacity', () => {
		assert.deepEqual(statRows(dagger).at(-1), { label: 'Modules', value: '1 / 1' });
	});
});
